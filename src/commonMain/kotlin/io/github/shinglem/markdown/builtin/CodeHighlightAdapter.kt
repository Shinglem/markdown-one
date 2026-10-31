package io.github.shinglem.markdown.builtin

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.shinglem.markdown.MarkdownConfig
import io.github.shinglem.markdown.adapter.AdapterKind
import io.github.shinglem.markdown.adapter.BlockAdapter
import io.github.shinglem.markdown.adapter.FormatId
import io.github.shinglem.markdown.adapter.Formats
import io.github.shinglem.markdown.model.MdBlock
import io.github.shinglem.markdown.text.MdCodeBlock

/**
 * 代码块：同步着色 + 复制按钮（外壳由 `text/MdCodeBlock` 提供）。
 *
 * 同步而不是异步着色：逐帧异步会出现「先无色、下一帧才上色」的闪烁。
 * 超过 [MarkdownConfig.maxSyncHighlightLength] 的块直接纯文本，避免卡住 UI。
 */
internal class CodeHighlightAdapter(private val config: MarkdownConfig) : BlockAdapter {
    override val id: String = "builtin.code"
    override val kind: AdapterKind = AdapterKind.Native
    override val priority: Int = 100

    override fun formats(): Set<FormatId> = setOf(Formats.Code)

    override fun isAvailable(): Boolean = true

    override fun canRender(block: MdBlock): Boolean =
        block is MdBlock.CodeBlock && !isMermaidLanguage(block.language)

    @Composable
    override fun Render(block: MdBlock, modifier: Modifier) {
        val codeBlock = block as MdBlock.CodeBlock
        MdCodeBlock(
            code = codeBlock.code,
            language = codeBlock.language,
            modifier = modifier,
            maxSyncHighlightLength = config.maxSyncHighlightLength,
        )
    }
}

/** 语言名是否按 mermaid 图表处理。 */
internal fun isMermaidLanguage(language: String?): Boolean {
    val name = language?.trim()?.lowercase() ?: return false
    return name == "mermaid" || name == "mmd"
}
