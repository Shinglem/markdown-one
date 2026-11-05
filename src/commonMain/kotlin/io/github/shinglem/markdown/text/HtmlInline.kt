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
        appendMarkdownMarkers(body)
        if (collapsed.endsWith(' ')) builder.append(' ')
    }

    /**
     * 文本节点里的 Markdown 标记还原成样式。
     *
     * `HtmlNormalizer` 在解析前就把 `<b>` / `<i>` / `<del>` / `<code>` / `<a>` / `<img>`
     * 改写成等价 Markdown 标记（行内 HTML 与 HTML 块走的是同一条预处理），
     * 所以 HTML 块里的文本节点本身就带着 `**粗体**`、`` `行内码` ``、`[文字](url)`。
     * 不还原的话界面上会看到裸露的星号与括号（实测）。
     */
    private fun appendMarkdownMarkers(text: String) {
        var cursor = 0
        for (match in INLINE_MARKER.findAll(text)) {
            if (match.range.first > cursor) builder.append(text.substring(cursor, match.range.first))
            val groups = match.groups
            when {
                groups[1] != null -> {
                    builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    appendMarkdownMarkers(groups[1]!!.value)
                    builder.pop()
                }

                groups[2] != null -> {
                    builder.pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                    appendMarkdownMarkers(groups[2]!!.value)
                    builder.pop()
                }

                groups[3] != null -> {
                    builder.pushStyle(styles.code)
                    builder.append(groups[3]!!.value)
                    builder.pop()
                }

                groups[4] != null -> {
                    builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    appendMarkdownMarkers(groups[4]!!.value)
                    builder.pop()
                }

                groups[6] != null -> {
                    val ref = HtmlImageRef(
                        key = HTML_IMAGE_KEY_PREFIX + groups[6]!!.value,
                        src = groups[6]!!.value,
                        alt = groups[5]?.value?.ifBlank { null },
                        widthPx = null,
                        heightPx = null,
                    )
                    images += ref
                    builder.appendInlineContent(ref.key, ref.alt ?: ref.src)
                }

                groups[8] != null -> {
                    builder.withLink(
                        LinkAnnotation.Url(groups[8]!!.value, styles.link, styles.linkListener),
                    ) {
                        appendMarkdownMarkers(groups[7]!!.value)
                    }
                }
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) builder.append(text.substring(cursor))
    }

    private companion object {
        val WHITESPACE = Regex("""\s+""")

        /**
         * 顺序即优先级：`**` 必须在 `*` 之前，图片 `![` 必须在链接 `[` 之前。
         *
         * 分组：1 粗体 / 2 删除线 / 3 行内码 / 4 斜体 / 5 图片 alt / 6 图片 src / 7 链接文字 / 8 链接地址。
         */
        val INLINE_MARKER = Regex(
            listOf(
                """\*\*(.+?)\*\*""",
                """~~(.+?)~~""",
                """`([^`]+)`""",
                """\*([^*\n]+?)\*""",
                """!\[([^\]]*)]\(([^)\s]+)\)""",
                """\[([^\]]+)]\(([^)\s]+)\)""",
            ).joinToString("|"),
        )
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
