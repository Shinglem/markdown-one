package io.github.shinglem.markdown.builtin

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.shinglem.markdown.MarkdownConfig
import io.github.shinglem.markdown.MarkdownText
import io.github.shinglem.markdown.adapter.AdapterKind
import io.github.shinglem.markdown.adapter.BlockAdapter
import io.github.shinglem.markdown.adapter.FormatId
import io.github.shinglem.markdown.model.MdBlock

/**
 * `<details><summary>…</summary>…</details>` 折叠块。
 *
 * 解析层把被 GFM 拆散的多个块合并成一个 `Custom("details")`（见
 * `GfmMarkdownParser.mergeDetails`），这里负责把它画成可展开的卡片：
 * summary 当标题，body 交给同一个 [MarkdownText] 递归渲染（里面的列表/代码/公式照常工作）。
 *
 * 展开状态默认按 `<details open>` 决定；不引 WebView，也不执行任何脚本。
 */
internal class DetailsAdapter(private val config: MarkdownConfig) : BlockAdapter {

    override val id: String = "builtin.details"
    override val kind: AdapterKind = AdapterKind.Native
    override val priority: Int = 110

    override fun formats(): Set<FormatId> = setOf(FormatId("details"))

    override fun isAvailable(): Boolean = true

    override fun canRender(block: MdBlock): Boolean = block is MdBlock.Custom && block.type == "details"

    @Composable
    override fun Render(block: MdBlock, modifier: Modifier) {
        val raw = block.raw
        val summary = remember(raw) { extract(raw, SUMMARY).trim() }
        val body = remember(raw) { extract(raw, BODY) }
        val initiallyOpen = remember(raw) { OPEN.containsMatchIn(raw) }
        var expanded by remember(raw) { mutableStateOf(initiallyOpen) }

        val scheme = MaterialTheme.colorScheme
        Column(
            modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .background(scheme.surfaceContainer, RoundedCornerShape(8.dp))
                .animateContentSize(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 不引 material-icons-extended（整包几 MB），折叠箭头用文本符号即可。
                Text(
                    text = if (expanded) "▾" else "▸",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    text = summary.ifBlank { "详情" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurface,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            if (expanded && body.isNotBlank()) {
                // 递归渲染：body 里的 markdown / 代码 / 公式 / 嵌套 details 都能用。
                MarkdownText(
                    markdown = body,
                    config = config,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                )
            }
        }
    }

    private fun extract(raw: String, regex: Regex): String {
        val match = regex.find(raw) ?: return ""
        return match.groupValues.getOrNull(1).orEmpty()
    }

    private companion object {
        val SUMMARY = Regex("""<summary[^>]*>(.*?)</summary>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val BODY = Regex("""</summary[^>]*>(.*?)</details>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val OPEN = Regex("""<details[^>]*\bopen\b""", RegexOption.IGNORE_CASE)
    }
}
