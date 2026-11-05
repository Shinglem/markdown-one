package io.github.shinglem.markdown.builtin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.rememberAsyncImagePainter
import io.github.shinglem.markdown.MarkdownConfig
import io.github.shinglem.markdown.adapter.AdapterKind
import io.github.shinglem.markdown.adapter.BlockAdapter
import io.github.shinglem.markdown.adapter.FormatId
import io.github.shinglem.markdown.adapter.Formats
import io.github.shinglem.markdown.model.MdBlock
import io.github.shinglem.markdown.parse.HtmlNode
import io.github.shinglem.markdown.parse.HtmlSanitizer
import io.github.shinglem.markdown.parse.HtmlStyle
import io.github.shinglem.markdown.text.HtmlImageRef
import io.github.shinglem.markdown.text.HtmlInlineStyles
import io.github.shinglem.markdown.text.LocalMdColors
import io.github.shinglem.markdown.text.LocalMdDimens
import io.github.shinglem.markdown.text.LocalMdPadding
import io.github.shinglem.markdown.text.LocalMdTypography
import io.github.shinglem.markdown.text.MdCodeBlock
import io.github.shinglem.markdown.text.buildHtmlInline
import io.github.shinglem.markdown.text.rememberMdInlineStyles

/**
 * 原始 HTML 块的**原生渲染**：ksoup 解析 → 白名单清洗 → Compose 块树 + 行内 `AnnotatedString`。
 *
 * 为什么不用内嵌浏览器：没有 310 MB 的 Chromium、没有渲染进程、没有脚本攻击面；代价只支持
 * 「标签语义 + 极少量样式」，而这恰好是 LLM 输出 HTML 的实际形态（见 `.design/待修复问题.md` #9）。
 *
 * 与 [HtmlContainerAdapter] 的分工：那个是「剥壳后交回 Markdown 递归」的兜底，这里按 DOM 结构
 * 逐块渲染（标题层级、列表嵌套、引用、代码块都按标签语义走），两者共用同一份白名单之外的
 * 行内改写（`HtmlNormalizer` 已在解析前处理过 `<b>/<i>/<code>/<a>/<img>/<br>`）。
 *
 * 病态输入（超深 / 超节点数 / 解析失败）整块回落源码，绝不出现"半个页面"。
 */
internal class HtmlNativeAdapter(@Suppress("unused") private val config: MarkdownConfig) : BlockAdapter {

    override val id: String = "builtin.html.native"
    override val kind: AdapterKind = AdapterKind.Native
    override val priority: Int = 115

    override fun formats(): Set<FormatId> = setOf(Formats.Html)

    override fun isAvailable(): Boolean = true

    override fun canRender(block: MdBlock): Boolean = block is MdBlock.Html

    @Composable
    override fun Render(block: MdBlock, modifier: Modifier) {
        val sanitized = remember(block.raw) { HtmlSanitizer.sanitize(block.raw) }
        val nodes = sanitized.nodes
        if (nodes == null || nodes.isEmpty()) {
            SourceFallbackAdapter.Render(block, modifier)
            return
        }
        HtmlBlockFlow(nodes, modifier.fillMaxWidth(), depth = 0)
    }
}

/** 一组 HTML 节点按「行内片段 / 块级元素」分组渲染。 */
@Composable
private fun HtmlBlockFlow(
    nodes: List<HtmlNode>,
    modifier: Modifier = Modifier,
    depth: Int = 0,
    style: TextStyle = LocalMdTypography.current.paragraph,
) {
    if (depth > HtmlSanitizer.MAX_DEPTH) return
    Column(modifier) {
        groupHtmlRuns(nodes).forEach { run ->
            when (run) {
                is HtmlRun.Inline -> HtmlInlineLine(run.nodes, style, Modifier.fillMaxWidth())
                is HtmlRun.Block -> HtmlBlockElement(run.element, depth, style)
            }
        }
    }
}

/** 行内片段：一次构建成 `AnnotatedString`，图片走 inline content。 */
@Composable
private fun HtmlInlineLine(nodes: List<HtmlNode>, style: TextStyle, modifier: Modifier) {
    val mdStyles = rememberMdInlineStyles()
    val colors = LocalMdColors.current
    val styles = remember(mdStyles, colors) {
        HtmlInlineStyles(
            link = mdStyles.link,
            code = mdStyles.code.copy(background = colors.inlineCodeBackground),
            mark = mdStyles.highlight,
            linkListener = mdStyles.linkListener,
        )
    }
    val inline = remember(nodes, styles) { buildHtmlInline(nodes, styles) }
    if (inline.text.isEmpty() && inline.images.isEmpty()) return
    val inlineContent = rememberHtmlInlineContent(inline.images, style)
    BasicText(
        text = inline.text,
        modifier = modifier,
        style = style,
        inlineContent = inlineContent,
    )
}

@Composable
private fun rememberHtmlInlineContent(images: List<HtmlImageRef>, style: TextStyle): Map<String, InlineTextContent> {
    if (images.isEmpty()) return emptyMap()
    val density = LocalDensity.current
    val fontPx = with(density) {
        (if (style.fontSize.value > 0f) style.fontSize else 14.sp).toPx()
    }
    return remember(images, style.fontSize, fontPx) {
        images.associate { image ->
            val width = image.widthPx?.let { px -> with(density) { px.dp.toPx() } } ?: (fontPx * 3f)
            val height = image.heightPx?.let { px -> with(density) { px.dp.toPx() } } ?: (width * 1.4f / 3f)
            image.key to InlineTextContent(
                Placeholder(
                    width = with(density) { width.toSp() },
                    height = with(density) { height.toSp() },
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                ),
            ) { alternateText ->
                Image(
                    painter = rememberAsyncImagePainter(image.src),
                    contentDescription = image.alt ?: alternateText,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** 块级元素：按标签语义落到 Compose 块（不做布局级 CSS）。 */
@Composable
private fun HtmlBlockElement(element: HtmlNode.Element, depth: Int, style: TextStyle) {
    val typography = LocalMdTypography.current
    val padding = LocalMdPadding.current
    val dens = LocalMdDimens.current
    val colors = LocalMdColors.current
    // `<center>` 的语义就是居中；`align` 属性已在清洗阶段并入 textAlign。
    val htmlStyle = if (element.style.textAlign == null && element.tag == "center") {
        element.style.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    } else {
        element.style
    }
    when (element.tag) {
        "h1", "h2", "h3", "h4", "h5", "h6" -> {
            val level = element.tag.last().digitToInt()
            HtmlInlineLine(
                element.children,
                typography.heading(level).mergeHtml(htmlStyle),
                Modifier.fillMaxWidth().padding(vertical = padding.block),
            )
        }

        "p" -> HtmlInlineLine(
            element.children,
            style.mergeHtml(htmlStyle),
            Modifier.fillMaxWidth().padding(vertical = padding.block),
        )

        "div", "section", "article", "center", "span", "figure", "details", "summary" ->
            HtmlBlockFlow(
                element.children,
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                depth + 1,
                style.mergeHtml(htmlStyle),
            )

        "blockquote" -> Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = padding.block)
                .drawBehind {
                    drawRect(
                        color = colors.dividerColor,
                        topLeft = Offset.Zero,
                        size = Size(dens.blockQuoteThickness.toPx(), size.height),
                    )
                }
                .padding(start = 12.dp),
        ) {
            HtmlBlockFlow(element.children, Modifier.fillMaxWidth(), depth + 1, typography.quote)
        }

        "ul", "ol" -> HtmlList(element, depth, style)
        "pre" -> MdCodeBlock(
            code = textContent(element).trimEnd('\n'),
            language = null,
            modifier = Modifier.fillMaxWidth(),
            maxSyncHighlightLength = 0,
        )

        "hr" -> Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .height(dens.dividerThickness)
                .background(colors.dividerColor),
        )

        "dl" -> HtmlBlockFlow(element.children, Modifier.fillMaxWidth(), depth + 1, style)
        "dt" -> HtmlInlineLine(
            element.children,
            style.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
            Modifier.fillMaxWidth().padding(top = 4.dp),
        )

        "dd" -> HtmlBlockFlow(
            element.children,
            Modifier.fillMaxWidth().padding(start = 16.dp),
            depth + 1,
            style,
        )

        "figcaption" -> HtmlInlineLine(
            element.children,
            style.copy(
                color = if (style.color == Color.Unspecified) colors.text.copy(alpha = 0.7f) else style.color,
                fontSize = 13.sp,
            ),
            Modifier.fillMaxWidth().padding(vertical = 2.dp),
        )

        "table" -> HtmlTable(element, depth, style)
        "img" -> HtmlBlockImage(element)
        else -> HtmlBlockFlow(element.children, Modifier.fillMaxWidth(), depth + 1, style)
    }
}

@Composable
private fun HtmlList(element: HtmlNode.Element, depth: Int, style: TextStyle) {
    val items = element.children.filterIsInstance<HtmlNode.Element>().filter { it.tag == "li" }
    val ordered = element.tag == "ol"
    val start = element.attrs["start"]?.toIntOrNull() ?: 1
    Column(Modifier.fillMaxWidth().padding(start = 12.dp * depth, top = 2.dp, bottom = 2.dp)) {
        items.forEachIndexed { index, item ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(
                    text = if (ordered) "${start + index}." else bulletFor(depth),
                    style = style,
                    modifier = Modifier.width(20.dp),
                )
                Box(Modifier.weight(1f)) {
                    HtmlBlockFlow(item.children, Modifier.fillMaxWidth(), depth + 1, style)
                }
            }
        }
    }
}

@Composable
private fun HtmlTable(element: HtmlNode.Element, depth: Int, style: TextStyle) {
    val rows = rowsOf(element)
    if (rows.isEmpty()) {
        HtmlBlockFlow(element.children, Modifier.fillMaxWidth(), depth + 1, style)
        return
    }
    val columns = rows.maxOf { it.second.size }
    val colors = LocalMdColors.current
    val dimens = LocalMdDimens.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(colors.tableBackground),
    ) {
        rows.forEachIndexed { rowIndex, (rowElement, cells) ->
            if (rowIndex > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(dimens.dividerThickness)
                        .background(colors.dividerColor.copy(alpha = 0.3f)),
                )
            }
            val header = rowElement.tag == "th" || rowElement.children.any {
                (it as? HtmlNode.Element)?.tag == "th"
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                cells.forEach { cell ->
                    HtmlInlineLine(
                        cell.children,
                        style.copy(
                            fontWeight = if (header) androidx.compose.ui.text.font.FontWeight.SemiBold else null,
                        ),
                        Modifier.weight(1f).padding(dimens.tableCellPadding),
                    )
                }
                repeat((columns - cells.size).coerceAtLeast(0)) {
                    Box(Modifier.weight(1f).padding(dimens.tableCellPadding))
                }
            }
        }
    }
}

/** 表格的行与单元格（`tbody` 透明展开）。 */
private fun rowsOf(table: HtmlNode.Element): List<Pair<HtmlNode.Element, List<HtmlNode.Element>>> {
    val rows = mutableListOf<Pair<HtmlNode.Element, List<HtmlNode.Element>>>()
    fun visit(node: HtmlNode.Element) {
        when (node.tag) {
            "tr" -> rows += node to node.children.filterIsInstance<HtmlNode.Element>()
                .filter { it.tag == "td" || it.tag == "th" }

            "thead", "tbody", "tfoot" -> node.children.filterIsInstance<HtmlNode.Element>().forEach(::visit)
            else -> Unit
        }
    }
    table.children.filterIsInstance<HtmlNode.Element>().forEach(::visit)
    return rows
}

@Composable
private fun HtmlBlockImage(element: HtmlNode.Element) {
    val src = element.attrs["src"] ?: return
    val width = element.attrs["width"]?.trim()?.toIntOrNull()
    val height = element.attrs["height"]?.trim()?.toIntOrNull()
    val modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)
        .then(width?.let { Modifier.width(it.dp) } ?: Modifier.fillMaxWidth())
        .then(height?.let { Modifier.height(it.dp) } ?: Modifier)
    Box(modifier) {
        Image(
            painter = rememberAsyncImagePainter(src),
            contentDescription = element.attrs["alt"],
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** 文本内容（递归取所有文本节点），给 `pre` 用。 */
internal fun textContent(node: HtmlNode): String = when (node) {
    is HtmlNode.Text -> node.text
    is HtmlNode.Element -> node.children.joinToString("") { textContent(it) }
}

/** 分组结果：连续的行内节点合成一段，块级元素各自成块。 */
internal sealed interface HtmlRun {
    class Inline(val nodes: List<HtmlNode>) : HtmlRun

    class Block(val element: HtmlNode.Element) : HtmlRun
}

internal fun groupHtmlRuns(nodes: List<HtmlNode>): List<HtmlRun> {
    val runs = mutableListOf<HtmlRun>()
    val inline = mutableListOf<HtmlNode>()

    fun flush() {
        if (inline.isNotEmpty()) {
            runs += HtmlRun.Inline(inline.toList())
            inline.clear()
        }
    }

    nodes.forEach { node ->
        val element = node as? HtmlNode.Element
        if (element != null && element.tag in BLOCK_TAGS) {
            flush()
            runs += HtmlRun.Block(element)
        } else {
            inline += node
        }
    }
    flush()
    return runs
}

private fun TextStyle.mergeAlign(align: androidx.compose.ui.text.style.TextAlign?): TextStyle =
    if (align == null) this else copy(textAlign = align)

/**
 * 把 HTML 的 `style` 子集并入块级 [TextStyle]。
 *
 * 布局属性（display / flex / position / margin…）压根没进 [HtmlStyle]，所以这里不用二次过滤。
 * 之前只并了 textAlign，导致 `<div style="font-size:28px">` 这类块级字号被丢掉（实测）。
 */
private fun TextStyle.mergeHtml(html: HtmlStyle): TextStyle = copy(
    color = html.color ?: color,
    fontSize = html.fontSize ?: fontSize,
    fontWeight = html.fontWeight ?: fontWeight,
    fontStyle = html.fontStyle ?: fontStyle,
    textAlign = html.textAlign ?: textAlign,
)

private fun bulletFor(depth: Int): String = when (depth % 3) {
    1 -> "◦"
    2 -> "▪"
    else -> "•"
}

/** 文档流里的块级标签（其余按行内处理）。 */
internal val BLOCK_TAGS: Set<String> = setOf(
    "p", "div", "section", "article", "center", "h1", "h2", "h3", "h4", "h5", "h6",
    "ul", "ol", "li", "blockquote", "pre", "hr", "table", "thead", "tbody", "tfoot", "tr",
    "td", "th", "caption", "figure", "figcaption", "details", "summary", "dl", "dt", "dd",
)
