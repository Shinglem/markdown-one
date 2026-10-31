package io.github.shinglem.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.mikepenz.markdown.compose.LocalImageTransformer
import com.mikepenz.markdown.compose.LocalMarkdownAnimations
import com.mikepenz.markdown.compose.LocalMarkdownAnnotator
import com.mikepenz.markdown.compose.LocalMarkdownColors
import com.mikepenz.markdown.compose.LocalMarkdownComponents
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.compose.LocalMarkdownDimens
import com.mikepenz.markdown.compose.LocalMarkdownExtendedSpans
import com.mikepenz.markdown.compose.LocalMarkdownInlineContent
import com.mikepenz.markdown.compose.LocalMarkdownPadding
import com.mikepenz.markdown.compose.LocalMarkdownTypography
import com.mikepenz.markdown.compose.LocalReferenceLinkHandler
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.MarkdownTypography
import com.mikepenz.markdown.model.ReferenceLinkHandlerImpl
import com.mikepenz.markdown.model.markdownAnimations
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.model.markdownDimens
import com.mikepenz.markdown.model.markdownExtendedSpans
import com.mikepenz.markdown.model.markdownInlineContent
import com.mikepenz.markdown.model.markdownPadding
import io.github.shinglem.markdown.adapter.LocalIsStreaming
import io.github.shinglem.markdown.builtin.projectMarkdownComponents
import io.github.shinglem.markdown.adapter.buildAdapterRegistry
import io.github.shinglem.markdown.parse.GfmMarkdownParser

/**
 * 渲染入口。
 *
 * 流程：解析成块 → 逐块向适配器链要渲染器 → 全部处理不了时由源码兜底适配器接管。
 * 块之间用 [Column] 排列（不嵌套 LazyColumn，避免无界高度约束导致崩溃）。
 *
 * 这里同时提供 mikepenz 的一整套 CompositionLocal：我们只用它的 element composable，
 * 不经过它的 `Markdown()` 入口，因此必须自己把这些默认值铺好。
 */
@Composable
public fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
    config: MarkdownConfig = MarkdownConfig(),
    textAlign: TextAlign? = null,
    fillWidth: Boolean = true,
) {
    val registry = remember(config) { buildAdapterRegistry(config) }
    val document = remember(markdown, streaming) { GfmMarkdownParser().parse(markdown, streaming) }
    val components = remember { projectMarkdownComponents() }
    val referenceLinks = remember { ReferenceLinkHandlerImpl() }

    // 行内公式：先从 AST 收集整篇出现过的 tex 建表，annotator 里按 key 命中同一条。
    val source = document.blocks.firstOrNull()?.source ?: markdown
    val inlineTexes = remember(document.blocks) { collectInlineTexes(document.blocks, source) }
    val mathContents = mathInlineContents(inlineTexes)
    val annotator = rememberMathAnnotator()
    val inlineContent = markdownInlineContent(mathContents)

    // 主题可在运行时切换（明暗），所以逐个取 CompositionLocal 当前值，而不是写死默认。
    val theme = config.theme
    val colors = theme?.colors ?: markdownColor()
    val baseTypography = theme?.typography ?: markdownTypography()
    val typography = remember(baseTypography, textAlign) {
        if (textAlign == null) baseTypography else alignedTypography(baseTypography, textAlign)
    }
    val dimens = theme?.dimens ?: markdownDimens()
    val padding = theme?.padding ?: markdownPadding()

    Box(modifier = if (theme?.background != Color.Unspecified && theme != null) {
        modifier.background(theme.background)
    } else {
        modifier
    }) {
        CompositionLocalProvider(
            LocalIsStreaming provides streaming,
            LocalReferenceLinkHandler provides referenceLinks,
            LocalMarkdownColors provides colors,
            LocalMarkdownTypography provides typography,
            LocalMarkdownDimens provides dimens,
            LocalMarkdownPadding provides padding,
            LocalImageTransformer provides Coil3ImageTransformerImpl,
            LocalMarkdownAnnotator provides annotator,
            LocalMarkdownExtendedSpans provides markdownExtendedSpans(),
            LocalMarkdownInlineContent provides inlineContent,
            LocalMarkdownComponents provides components,
            LocalMarkdownAnimations provides markdownAnimations(),
        ) {
            Column(if (fillWidth) Modifier.fillMaxWidth() else Modifier) {
                document.blocks.forEach { block ->
                    val adapter = registry.select(block, streaming)
                    if (adapter != null) {
                        // 用块内容参与 key：切到另一个文档时块 key 仍是 b0/b1，只按 key 去重会漏报。
                        LaunchedEffect(block.key, block.raw, adapter.id) {
                            config.diagnostics.onRoute(block.key, block.formats, adapter.id, adapter.kind)
                        }
                        key(block.key) {
                            val content: @Composable () -> Unit = {
                                adapter.Render(block, if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                            }
                            val needsViewport = config.viewport.enabled &&
                                config.viewport.matches(block.formats.map { it.value }.toSet())
                            if (needsViewport) {
                                BlockViewport(config.viewport, content)
                            } else {
                                content()
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 只改水平对齐的 [MarkdownTypography]；其余字段原样委托给当前主题。
 *
 * 用 typography 而不是布局约束来做居中：mikepenz 的段落是整宽 `Text`，
 * 外层 `Box` 怎么对齐都不影响里面的文字与行内图片。
 */
private fun alignedTypography(base: MarkdownTypography, align: TextAlign): MarkdownTypography =
    AlignedTypography(base, align)

private class AlignedTypography(
    private val base: MarkdownTypography,
    private val align: TextAlign,
) : MarkdownTypography by base {
    private fun TextStyle.aligned(): TextStyle = copy(textAlign = align)

    override val text: TextStyle get() = base.text.aligned()
    override val paragraph: TextStyle get() = base.paragraph.aligned()
    override val h1: TextStyle get() = base.h1.aligned()
    override val h2: TextStyle get() = base.h2.aligned()
    override val h3: TextStyle get() = base.h3.aligned()
    override val h4: TextStyle get() = base.h4.aligned()
    override val h5: TextStyle get() = base.h5.aligned()
    override val h6: TextStyle get() = base.h6.aligned()
}
