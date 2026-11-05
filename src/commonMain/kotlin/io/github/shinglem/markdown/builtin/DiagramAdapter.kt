package io.github.shinglem.markdown.builtin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.hrm.diagram.core.ir.SourceLanguage
import com.hrm.diagram.core.theme.DiagramTheme
import com.hrm.diagram.render.Diagram
import com.hrm.diagram.render.compose.DiagramSurface
import com.hrm.diagram.render.compose.rememberDiagramRenderState
import com.hrm.diagram.render.theme.material3
import io.github.shinglem.markdown.MarkdownConfig
import io.github.shinglem.markdown.adapter.AdapterKind
import io.github.shinglem.markdown.adapter.BlockAdapter
import io.github.shinglem.markdown.adapter.FormatId
import io.github.shinglem.markdown.adapter.Formats
import io.github.shinglem.markdown.adapter.LocalIsStreaming
import io.github.shinglem.markdown.model.MdBlock

/**
 * 图表块：Mermaid / PlantUML / DOT 一把渲染（`io.github.huarangmeng:diagram-render`，纯 Compose）。
 *
 * 相比被替换掉的 `mermaid-compose`：图族更多（含 PlantUML、Graphviz DOT）、跟随 M3 配色
 * （旧引擎硬编码白底，暗色主题里刺眼）、能识别没有语言标记的 fence。
 *
 * 流式期间**不画图** —— 每帧全量重排会把滚动抖掉；回答结束后再出图（与旧实现一致）。
 * 解析失败时不留空白：显示 [MarkdownConfig.mermaidFallbackText] 与源码，并把失败上报给
 * [MarkdownConfig.diagnostics]。
 */
internal class DiagramAdapter(private val config: MarkdownConfig) : BlockAdapter {
    override val id: String = "builtin.diagram"
    override val kind: AdapterKind = AdapterKind.Native
    override val priority: Int = 100

    override fun formats(): Set<FormatId> = setOf(Formats.Mermaid, Formats.Code)

    override fun isAvailable(): Boolean = true

    override fun canRender(block: MdBlock): Boolean =
        block is MdBlock.CodeBlock && diagramLanguage(block) != null

    @Composable
    override fun Render(block: MdBlock, modifier: Modifier) {
        val code = block as MdBlock.CodeBlock
        if (LocalIsStreaming.current) {
            Text(
                text = "图表生成中…",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier.padding(vertical = 8.dp),
            )
            return
        }
        val language = remember(code.code, code.language) { diagramLanguage(code) } ?: SourceLanguage.MERMAID
        val theme = rememberDiagramTheme()
        val state = rememberDiagramRenderState(
            source = code.code,
            theme = theme,
            languageHint = language,
        )
        val snapshot = state.snapshot
        val failed = snapshot.isFinal && snapshot.drawCommands.isEmpty()
        if (failed) {
            LaunchedEffect(code.code) { config.diagnostics.onFailure(id, block.key, null) }
            Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    text = config.mermaidFallbackText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = code.code,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
            return
        }
        Box(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            DiagramSurface(
                state = state,
                modifier = Modifier.fillMaxWidth(),
                // 对话里不做缩放拖拽：手势由外层滚动容器负责，否则会抢走滚动。
                zoomEnabled = false,
            )
        }
    }
}

@Composable
private fun rememberDiagramTheme(): DiagramTheme {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) { DiagramTheme.material3(scheme) }
}

/**
 * 这个代码块该按哪种图表语言渲染；不是图表时返回 null。
 *
 * 优先看 fence 的语言标记；**没有**语言标记时才靠内容推断（`Diagram.detectSource`）——
 * 带 ```` ```kotlin ```` 的块里出现 `graph TD` 只是字符串，不能当成图。
 */
internal fun diagramLanguage(block: MdBlock.CodeBlock): SourceLanguage? {
    val explicit = when (block.language?.trim()?.lowercase()) {
        "mermaid", "mmd" -> SourceLanguage.MERMAID
        "plantuml", "puml" -> SourceLanguage.PLANTUML
        "dot", "graphviz" -> SourceLanguage.DOT
        else -> null
    }
    if (explicit != null) return explicit
    if (!block.language.isNullOrBlank()) return null
    // 内容推断只对短块做：长代码块的检测没必要（也不该逐帧跑）。
    if (block.code.length > MAX_DETECT_LENGTH) return null
    val detection = Diagram.detectSource(block.code, null)
    return detection.takeIf { it.shouldRouteToDiagram }?.language
}

private const val MAX_DETECT_LENGTH = 4_000
