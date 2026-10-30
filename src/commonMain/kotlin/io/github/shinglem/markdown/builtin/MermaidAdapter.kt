package io.github.shinglem.markdown.builtin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swithun.cmpmermaid.compose.MermaidDiagram
import com.swithun.cmpmermaid.core.GMResult
import io.github.shinglem.markdown.MarkdownConfig
import io.github.shinglem.markdown.adapter.AdapterKind
import io.github.shinglem.markdown.adapter.BlockAdapter
import io.github.shinglem.markdown.adapter.FormatId
import io.github.shinglem.markdown.adapter.Formats
import io.github.shinglem.markdown.adapter.LocalIsStreaming
import io.github.shinglem.markdown.model.MdBlock

/**
 * mermaid 图表：纯 Kotlin 引擎（`io.github.swithun-liu:mermaid-compose`）在 Compose 内直接绘制。
 *
 * 流式期间**不画图** —— 每帧全量重排会把滚动抖掉；回答结束后再出图。
 * 画不出来时返回 false 语义：由调用方走下一级适配器（JCEF 或源码兜底），不留空白。
 */
internal class MermaidAdapter(private val config: MarkdownConfig) : BlockAdapter {
    override val id: String = "builtin.mermaid"
    override val kind: AdapterKind = AdapterKind.Native
    override val priority: Int = 100

    override fun formats(): Set<FormatId> = setOf(Formats.Mermaid, Formats.code("mermaid"))

    override fun isAvailable(): Boolean = true

    override fun canRender(block: MdBlock): Boolean =
        block is MdBlock.CodeBlock && isMermaidLanguage(block.language)

    @Composable
    override fun Render(block: MdBlock, modifier: Modifier) {
        val codeBlock = block as MdBlock.CodeBlock
        if (LocalIsStreaming.current) {
            Text(
                text = "图表生成中…",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier.padding(vertical = 8.dp),
            )
            return
        }
        var failure: String? by remember(codeBlock.code) { mutableStateOf(null) }
        // 引擎需要一个**有界高度**的容器才会绘制；未知比例时先占 240dp，拿到场景尺寸后按比例自适应。
        var aspect: Float? by remember(codeBlock.code) { mutableStateOf(null) }
        val message = failure
        if (message != null) {
            Text(
                text = config.mermaidFallbackText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier.padding(bottom = 4.dp),
            )
            Text(
                text = codeBlock.code,
                style = MaterialTheme.typography.bodySmall,
                modifier = modifier,
            )
            return
        }
        val sizeModifier = aspect?.let { Modifier.aspectRatio(it) } ?: Modifier.height(240.dp)
        Box(modifier = modifier.fillMaxWidth().then(sizeModifier).padding(vertical = 8.dp)) {
            MermaidDiagram(
                source = codeBlock.code,
                modifier = Modifier.fillMaxSize(),
                onError = { info ->
                    failure = info.message
                    config.diagnostics.onFailure(id, block.key, null)
                },
                onRenderResult = { result ->
                    if (result is GMResult.Ok) {
                        val scene = result.value
                        if (scene.height > 0.0) aspect = scene.width / scene.height
                    }
                },
            )
        }
    }
}
