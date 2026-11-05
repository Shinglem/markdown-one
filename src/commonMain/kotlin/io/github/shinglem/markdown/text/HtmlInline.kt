package io.github.shinglem.markdown.text

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.TextUnit
import io.github.shinglem.markdown.parse.HtmlNode
import io.github.shinglem.markdown.parse.HtmlStyle

/** HTML 行内图片（渲染层用它建 inline content）。 */
internal class HtmlImageRef(
    val key: String,
    val src: String,
    val alt: String?,
    val widthPx: Int?,
    val heightPx: Int?,
)

/** 行内构建结果：文本 + 需要配套 inline content 的图片。 */
internal class HtmlInline(val text: AnnotatedString, val images: List<HtmlImageRef>)

/** 行内构建需要的样式（与 `MdInlineStyles` 分开：HTML 的标签集不同）。 */
internal data class HtmlInlineStyles(
    val link: TextLinkStyles,
    val code: SpanStyle,
    val mark: SpanStyle,
    val linkListener: LinkInteractionListener?,
)

internal const val HTML_IMAGE_KEY_PREFIX = "html_image:"

/**
 * 把一段 HTML 行内节点构建成 [AnnotatedString]。
 *
 * 与 Markdown 的行内构建器（`MdInline.kt`）分开实现：那边的入口是 GFM AST，
 * 这边是清洗后的 DOM；两者共用主题里的样式对象即可，不必把两套节点模型揉在一起。
 *
 * 空白处理按 HTML 语义：连续空白折叠成一个空格、行首行尾的空白丢掉。
 */
internal fun buildHtmlInline(nodes: List<HtmlNode>, styles: HtmlInlineStyles): HtmlInline {
    val walker = HtmlInlineWalker(styles)
    walker.appendChildren(nodes)
    return HtmlInline(walker.text(), walker.images)
}

private class HtmlInlineWalker(private val styles: HtmlInlineStyles) {
    private val builder = AnnotatedString.Builder()
    val images: MutableList<HtmlImageRef> = mutableListOf()

    fun text(): AnnotatedString = builder.toAnnotatedString().trimEdges()

    fun appendChildren(children: List<HtmlNode>) {
        children.forEach { child ->
            when (child) {
                is HtmlNode.Text -> appendCollapsed(child.text)
                is HtmlNode.Element -> appendElement(child)
            }
        }
    }

    private fun appendElement(element: HtmlNode.Element) {
        val hasStyle = !element.style.isEmpty
        if (hasStyle) builder.pushStyle(element.style.toSpanStyle())
        when (element.tag) {
            "br" -> builder.append('\n')
            "wbr" -> Unit
            "b", "strong" -> styled(SpanStyle(fontWeight = FontWeight.Bold), element.children)
            "i", "em", "cite", "dfn", "var" -> styled(SpanStyle(fontStyle = FontStyle.Italic), element.children)
            "u", "ins" -> styled(SpanStyle(textDecoration = TextDecoration.Underline), element.children)
            "s", "del", "strike" -> styled(SpanStyle(textDecoration = TextDecoration.LineThrough), element.children)
            "code", "kbd", "samp", "tt" -> styled(styles.code, element.children)
            "mark" -> styled(styles.mark, element.children)
            "sub" -> styled(SpanStyle(baselineShift = BaselineShift.Subscript), element.children)
            "sup" -> styled(SpanStyle(baselineShift = BaselineShift.Superscript), element.children)
            "img" -> appendImage(element)
            "a" -> appendLink(element)
            else -> appendChildren(element.children)
        }
        if (hasStyle) builder.pop()
    }

    private fun styled(style: SpanStyle, children: List<HtmlNode>) {
        builder.pushStyle(style)
        appendChildren(children)
        builder.pop()
    }

    private fun appendLink(element: HtmlNode.Element) {
        val href = element.attrs["href"]
        if (href.isNullOrBlank()) {
            appendChildren(element.children)
            return
        }
        builder.withLink(LinkAnnotation.Url(href, styles.link, styles.linkListener)) {
            appendChildren(element.children)
        }
    }

    private fun appendImage(element: HtmlNode.Element) {
        val src = element.attrs["src"] ?: return
        val alt = element.attrs["alt"]
        val ref = HtmlImageRef(
            key = HTML_IMAGE_KEY_PREFIX + src,
            src = src,
            alt = alt,
            widthPx = element.attrs["width"]?.trim()?.toIntOrNull()?.takeIf { it > 0 },
            heightPx = element.attrs["height"]?.trim()?.toIntOrNull()?.takeIf { it > 0 },
        )
        images += ref
        builder.appendInlineContent(ref.key, alt ?: src)
    }

    /** 连续空白折叠成一个空格；行首不产生前导空格。 */
    private fun appendCollapsed(text: String) {
        val collapsed = text.replace(WHITESPACE, " ")
        val body = collapsed.trim()
        val current = builder.length
        if (body.isEmpty()) {
            if (current > 0) builder.append(' ')
            return
        }
        if (collapsed.startsWith(' ') && current > 0) builder.append(' ')
        builder.append(body)
        if (collapsed.endsWith(' ')) builder.append(' ')
    }

    private companion object {
        val WHITESPACE = Regex("""\s+""")
    }
}

/** 去掉整段首尾的空格与换行（块与块之间的留白由布局负责）。 */
private fun AnnotatedString.trimEdges(): AnnotatedString {
    var start = 0
    var end = length
    while (start < end && text[start].isWhitespace()) start++
    while (end > start && text[end - 1].isWhitespace()) end--
    if (start == 0 && end == length) return this
    return subSequence(start, end)
}

/** `style` 子集 → [SpanStyle]。布局属性压根没进 [HtmlStyle]，所以这里不用二次过滤。 */
internal fun HtmlStyle.toSpanStyle(): SpanStyle = SpanStyle(
    color = color ?: Color.Unspecified,
    background = background ?: Color.Unspecified,
    fontSize = fontSize ?: TextUnit.Unspecified,
    fontWeight = fontWeight,
    fontStyle = fontStyle,
)
