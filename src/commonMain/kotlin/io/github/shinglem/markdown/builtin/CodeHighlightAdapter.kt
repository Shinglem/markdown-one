package io.github.shinglem.markdown.builtin

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.LocalMarkdownColors
import com.mikepenz.markdown.compose.LocalMarkdownDimens
import com.mikepenz.markdown.compose.LocalMarkdownPadding
import com.mikepenz.markdown.compose.elements.MarkdownCodeBackground
import com.mikepenz.markdown.m3.markdownTypography
import io.github.shinglem.markdown.MarkdownConfig
import io.github.shinglem.markdown.adapter.AdapterKind
import io.github.shinglem.markdown.adapter.BlockAdapter
import io.github.shinglem.markdown.adapter.FormatId
import io.github.shinglem.markdown.adapter.Formats
import io.github.shinglem.markdown.code.highlightCode
import io.github.shinglem.markdown.model.MdBlock

/**
 * 代码块：同步着色 + 复制按钮（复用 mikepenz 的代码块外壳）。
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
        val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        val highlighted: AnnotatedString = remember(codeBlock.code, codeBlock.language, dark, config.maxSyncHighlightLength) {
            if (codeBlock.code.length <= config.maxSyncHighlightLength) {
                highlightCode(codeBlock.code, codeBlock.language, dark)
            } else {
                AnnotatedString(codeBlock.code)
            }
        }
        MarkdownCodeBackground(
            color = LocalMarkdownColors.current.codeBackground,
            shape = RoundedCornerShape(LocalMarkdownDimens.current.codeBackgroundCornerSize),
            modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
            showHeader = true,
            language = codeBlock.language,
            code = codeBlock.code,
        ) {
            BasicText(
                text = highlighted,
                style = markdownTypography().code,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(LocalMarkdownPadding.current.codeBlock),
            )
        }
    }
}

/** 语言名是否按 mermaid 图表处理。 */
internal fun isMermaidLanguage(language: String?): Boolean {
    val name = language?.trim()?.lowercase() ?: return false
    return name == "mermaid" || name == "mmd"
}
