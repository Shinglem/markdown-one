package io.github.shinglem.markdown.builtin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.mikepenz.markdown.compose.LocalMarkdownTypography
import com.mikepenz.markdown.model.MarkdownTypography
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

        val align = remember(raw) {
            ALIGN.find(raw)?.let { match ->
                if (match.groupValues[1].equals("right", ignoreCase = true)) TextAlign.End else TextAlign.Center
            }
        }
        if (align == null) {
            MarkdownText(markdown = inner, config = config, modifier = modifier)
            return
        }

        val typography = LocalMarkdownTypography.current
        val aligned = remember(typography, align) { AlignedTypography(typography, align) }
        CompositionLocalProvider(LocalMarkdownTypography provides aligned) {
            MarkdownText(markdown = inner, config = config, modifier = modifier)
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

/** 只改水平对齐的 [MarkdownTypography]；其余字段原样委托给当前主题。 */
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
