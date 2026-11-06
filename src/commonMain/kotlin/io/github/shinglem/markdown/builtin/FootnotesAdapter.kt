package io.github.shinglem.markdown.builtin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.shinglem.markdown.MarkdownConfig
import io.github.shinglem.markdown.MarkdownText
import io.github.shinglem.markdown.adapter.AdapterKind
import io.github.shinglem.markdown.adapter.BlockAdapter
import io.github.shinglem.markdown.adapter.FormatId
import io.github.shinglem.markdown.adapter.Formats
import io.github.shinglem.markdown.model.MdBlock

/**
 * 脚注定义区（文档末尾）。
 *
 * 定义由 `parse/FootnoteNormalizer.kt` 在解析前抽出，这里把「序号 + 定义正文」重新拼成
 * 一个有序列表交给 [MarkdownText] 渲染 —— 定义正文本身还是 Markdown（可能有强调、行内代码、
 * 链接），走同一套渲染器比自己写行内解析可靠。列表续行按 3 格缩进（`1. ` 的宽度），
 * 多行定义因此不会被当成新段落从而断号。
 *
 * 正文里的引用已经被改写成 `[n](#fn-id)`，渲染成上标序号（见 `text/MdInline.kt`）。
 */
internal class FootnotesAdapter(private val config: MarkdownConfig) : BlockAdapter {

    override val id: String = "builtin.footnotes"
    override val kind: AdapterKind = AdapterKind.Native
    override val priority: Int = 105

    override fun formats(): Set<FormatId> = setOf(Formats.Footnotes)

    override fun isAvailable(): Boolean = true

    override fun canRender(block: MdBlock): Boolean = block is MdBlock.Footnotes && block.items.isNotEmpty()

    @Composable
    override fun Render(block: MdBlock, modifier: Modifier) {
        val footnotes = block as MdBlock.Footnotes
        val markdown = remember(footnotes.items) {
            numberedList(footnotes.items.map { Triple(it.number, it.markdown, it.id) })
        }
        val scheme = MaterialTheme.colorScheme
        Column(modifier.fillMaxWidth().padding(top = 12.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .height(1.dp)
                    .background(scheme.outlineVariant),
            )
            Text(
                text = "脚注",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp),
            )
            MarkdownText(markdown = markdown, config = config)
        }
    }

    /**
     * 「序号 + 定义正文 + ↩」拼成有序列表。
     *
     * 末尾的 `[↩](#fnref-id)` 是**回跳**：滚回正文里引用它的位置。锚点 `fnref-<id>` 由
     * `MarkdownText` 按块原文扫出来（本适配器跑在嵌套的 `MarkdownText` 里，自己解析不到
     * 那个锚点，靠外层的 handler 冒泡过去）。
     */
    private fun numberedList(items: List<Triple<Int, String, String>>): String = buildString {
        items.forEachIndexed { index, (number, body, id) ->
            if (index > 0) append('\n')
            append(number).append(". ")
            // 续行缩进 3 格（`1. ` 的宽度），多行定义不会被当成新段落而断号
            append(body.lines().joinToString("\n   "))
            append(" [↩](#fnref-").append(id).append(")")
        }
    }
}
