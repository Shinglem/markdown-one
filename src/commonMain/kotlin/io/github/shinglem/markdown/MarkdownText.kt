package io.github.shinglem.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import io.github.shinglem.markdown.adapter.LocalIsStreaming
import io.github.shinglem.markdown.adapter.buildAdapterRegistry
import io.github.shinglem.markdown.parse.GfmMarkdownParser
import io.github.shinglem.markdown.text.LocalMdAdapterRegistry
import io.github.shinglem.markdown.text.LocalMdColors
import io.github.shinglem.markdown.text.LocalMdDimens
import io.github.shinglem.markdown.text.LocalMdMathContent
import io.github.shinglem.markdown.text.LocalMdMaxSyncHighlightLength
import io.github.shinglem.markdown.text.LocalMdOnLinkClick
import io.github.shinglem.markdown.text.LocalMdPadding
import io.github.shinglem.markdown.text.LocalMdReferences
import io.github.shinglem.markdown.text.LocalMdTypography
import io.github.shinglem.markdown.text.collectLinkDefinitions
import io.github.shinglem.markdown.text.mdColors
import io.github.shinglem.markdown.text.mdDimens
import io.github.shinglem.markdown.text.mdPadding
import io.github.shinglem.markdown.text.mdTypography
import io.github.shinglem.markdown.text.withTextAlign

/**
 * 渲染入口。
 *
 * 流程：解析成块 → 逐块向适配器链要渲染器 → 全部处理不了时由源码兜底适配器接管。
 * 块之间用 [Column] 排列（不嵌套 LazyColumn，避免无界高度约束导致崩溃）。
 *
 * 这里铺的是本库自己的一套 CompositionLocal（`io.github.shinglem.markdown.text`），
 * 文本与块组件都从这里取主题，不再依赖第三方 renderer 的模型。
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

    // 主题可在运行时切换（明暗），所以逐个取当前值，而不是写死默认。
    val theme = config.theme
    val colors = theme?.colors ?: mdColors()
    val baseTypography = theme?.typography ?: mdTypography()
    val typography = remember(baseTypography, textAlign) {
        if (textAlign == null) baseTypography else baseTypography.withTextAlign(textAlign)
    }
    val dimens = theme?.dimens ?: mdDimens()
    val padding = theme?.padding ?: mdPadding()

    // 行内公式：先按整篇收集 tex 建表，构建器里按 key 命中同一条。
    // 颜色与字号显式传下去 —— 这时还不在主题的 CompositionLocal 作用域里。
    val source = document.blocks.firstOrNull()?.source ?: markdown
    val inlineTexes = remember(document.blocks) { collectInlineTexes(document.blocks, source) }
    val mathContents = mathInlineContents(inlineTexes, colors.text, typography.text.fontSize)
    val references = remember(document.blocks) {
        collectLinkDefinitions(source, document.blocks.map { it.node })
    }

    Box(
        modifier = if (theme != null && theme.background != Color.Unspecified) {
            modifier.background(theme.background)
        } else {
            modifier
        },
    ) {
        CompositionLocalProvider(
            LocalIsStreaming provides streaming,
            LocalMdColors provides colors,
            LocalMdTypography provides typography,
            LocalMdDimens provides dimens,
            LocalMdPadding provides padding,
            LocalMdMathContent provides mathContents,
            LocalMdReferences provides references,
            LocalMdAdapterRegistry provides registry,
            LocalMdOnLinkClick provides config.onLinkClick,
            LocalMdMaxSyncHighlightLength provides config.maxSyncHighlightLength,
        ) {
            // 选择框只包一次：每块一个 SelectionContainer 会让跨块选区断成若干段。
            SelectionContainer {
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
}
