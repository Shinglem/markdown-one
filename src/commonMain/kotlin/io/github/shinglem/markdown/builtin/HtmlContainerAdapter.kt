package io.github.shinglem.markdown.builtin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import io.github.shinglem.markdown.MarkdownConfig
import io.github.shinglem.markdown.MarkdownText
import io.github.shinglem.markdown.adapter.AdapterKind
import io.github.shinglem.markdown.adapter.BlockAdapter
import io.github.shinglem.markdown.adapter.FormatId
import io.github.shinglem.markdown.adapter.Formats
import io.github.shinglem.markdown.model.MdBlock

/**
 * 块级 HTML 容器（`<div align="center">…</div>`、`<center>…</center>`、`<p align=…>` 等）。
 *
 * 只做一件事：剥掉容器标签，把里面的内容交回 [MarkdownText] 递归渲染。
 * `align` 用「换一套 [MarkdownTypography]（textAlign）」实现，而不是靠布局居中 ——
 * mikepenz 的段落是整宽 `Text`，只有 textAlign 能真正让文字与行内图片居中。
 *
 * 剥完没有可见内容（纯 `<br>`、注释、脚本）时不画任何东西：这些内容本来就没有可见语义。
 */
internal class HtmlContainerAdapter(private val config: MarkdownConfig) : BlockAdapter {

    override val id: String = "builtin.html"
    override val kind: AdapterKind = AdapterKind.Native
    override val priority: Int = 105

    override fun formats(): Set<FormatId> = setOf(Formats.Html)

    override fun isAvailable(): Boolean = true

    override fun canRender(block: MdBlock): Boolean = block is MdBlock.Html

    @Composable
    override fun Render(block: MdBlock, modifier: Modifier) {
        val raw = block.raw
        val inner = remember(raw) { unwrap(raw) }
        if (inner.isBlank()) return
        if (inner == raw.trim()) {
            // 一个容器标签都没剥掉：说明这个 HTML 块不是我们认识的容器结构。
            // 直接显示源码 —— 绝不能递归渲染原文，否则自己会再次命中本适配器（栈溢出）。
            SourceFallbackAdapter.Render(block, modifier)
            return
        }

        // 内层禁用本适配器：内容里再出现裸 HTML 块时走源码兜底，杜绝递归。
        val nested = remember(config) {
            config.copy(disabledAdapterIds = config.disabledAdapterIds + id)
        }
        val align = remember(raw) {
            ALIGN.find(raw)?.let { match ->
                if (match.groupValues[1].equals("right", ignoreCase = true)) {
                    Alignment.TopEnd to TextAlign.End
                } else {
                    Alignment.TopCenter to TextAlign.Center
                }
            }
        }
        if (align == null) {
            MarkdownText(markdown = inner, config = nested, modifier = modifier)
        } else {
            // 两层都要：外层 Box 让整块贴着容器中/右，内层 textAlign 让多行文字各自居中。
            Box(modifier.fillMaxWidth(), contentAlignment = align.first) {
                MarkdownText(
                    markdown = inner,
                    config = nested,
                    modifier = Modifier.wrapContentWidth(),
                    textAlign = align.second,
                    fillWidth = false,
                )
            }
        }
    }

    /** 去掉最外层容器标签；不认识的外层标签保留，交给源码兜底显示。 */
    private fun unwrap(raw: String): String {
        var text = raw.trim()
        repeat(4) {
            val open = CONTAINER_OPEN.find(text) ?: return@repeat
            val close = CONTAINER_CLOSE.find(text) ?: return@repeat
            if (open.range.first != 0) return@repeat
            text = text.substring(open.range.last + 1, close.range.first).trim()
        }
        return if (TAG_ONLY.matches(text) && !IMG.containsMatchIn(text)) "" else text
    }

    private companion object {
        val ALIGN = Regex("""(?:align\s*=\s*["']?\s*(right|center)|<center\b)""", RegexOption.IGNORE_CASE)
        val IMG = Regex("""<img\b""", RegexOption.IGNORE_CASE)
        val CONTAINER_OPEN = Regex(
            """^<(div|center|p|section|article|span|font|small|big)\b[^>]*>""",
            RegexOption.IGNORE_CASE,
        )
        val CONTAINER_CLOSE = Regex(
            """</(div|center|p|section|article|span|font|small|big)\s*>\s*$""",
            RegexOption.IGNORE_CASE,
        )
        val TAG_ONLY = Regex("""^(?:\s*</?[a-zA-Z][\w-]*[^>]*>)+\s*$""")
    }
}
