package io.github.shinglem.markdown

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.compose.LocalImageTransformer
import com.mikepenz.markdown.compose.LocalMarkdownAnimations
import com.mikepenz.markdown.compose.LocalMarkdownAnnotator
import com.mikepenz.markdown.compose.LocalMarkdownColors
import com.mikepenz.markdown.compose.LocalMarkdownComponents
import com.mikepenz.markdown.compose.LocalMarkdownDimens
import com.mikepenz.markdown.compose.LocalMarkdownExtendedSpans
import com.mikepenz.markdown.compose.LocalMarkdownInlineContent
import com.mikepenz.markdown.compose.LocalMarkdownPadding
import com.mikepenz.markdown.compose.LocalMarkdownTypography
import com.mikepenz.markdown.compose.LocalReferenceLinkHandler
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.NoOpImageTransformerImpl
import com.mikepenz.markdown.model.ReferenceLinkHandlerImpl
import com.mikepenz.markdown.model.markdownAnimations
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.model.markdownDimens
import com.mikepenz.markdown.model.markdownExtendedSpans
import com.mikepenz.markdown.model.markdownInlineContent
import com.mikepenz.markdown.model.markdownPadding
import io.github.shinglem.markdown.adapter.LocalIsStreaming
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
) {
    val registry = remember(config) { buildAdapterRegistry(config) }
    val document = remember(markdown, streaming) { GfmMarkdownParser().parse(markdown, streaming) }
    val components = remember { markdownComponents() }
    val referenceLinks = remember { ReferenceLinkHandlerImpl() }

    CompositionLocalProvider(
        LocalIsStreaming provides streaming,
        LocalReferenceLinkHandler provides referenceLinks,
        LocalMarkdownColors provides markdownColor(),
        LocalMarkdownTypography provides markdownTypography(),
        LocalMarkdownDimens provides markdownDimens(),
        LocalMarkdownPadding provides markdownPadding(),
        LocalImageTransformer provides NoOpImageTransformerImpl(),
        LocalMarkdownAnnotator provides markdownAnnotator(),
        LocalMarkdownExtendedSpans provides markdownExtendedSpans(),
        LocalMarkdownInlineContent provides markdownInlineContent(),
        LocalMarkdownComponents provides components,
        LocalMarkdownAnimations provides markdownAnimations(),
    ) {
        Column(modifier = modifier) {
            document.blocks.forEach { block ->
                val adapter = registry.select(block, streaming)
                if (adapter != null) {
                    // 用块内容参与 key：切到另一个文档时块 key 仍是 b0/b1，只按 key 去重会漏报。
                    androidx.compose.runtime.LaunchedEffect(block.key, block.raw, adapter.id) {
                        config.diagnostics.onRoute(block.key, block.formats, adapter.id, adapter.kind)
                    }
                    key(block.key) {
                        adapter.Render(block, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}
