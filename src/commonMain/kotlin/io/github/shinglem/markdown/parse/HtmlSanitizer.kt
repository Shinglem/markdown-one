package io.github.shinglem.markdown.parse

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode

/**
 * 清洗后的 HTML 树。与 ksoup 的节点解耦：渲染层只认这个模型，单测也能直接断言。
 *
 * 白名单照 GitHub `html-pipeline` 的 `sanitization_filter.rb`：
 * **没有 `class`、没有 `on*`**；`style` 只保留能映射到 [TextStyle] 的 6 个属性
 * （见 [HtmlStyle]），布局属性一律静默忽略。
 */
internal sealed interface HtmlNode {
    data class Element(
        val tag: String,
        val attrs: Map<String, String>,
        val style: HtmlStyle,
        val children: List<HtmlNode>,
    ) : HtmlNode

    data class Text(val text: String) : HtmlNode
}

/**
 * `style` 里我们**认**的 6 个属性，其余（display / flex / grid / position / float /
 * margin / padding / z-index / transform …）静默忽略 —— 实现它们需要迷你 CSS 引擎 +
 * 布局算法，收益与投入不成比例（依据见 `.design/待修复问题.md` #9）。
 */
internal data class HtmlStyle(
    val color: Color? = null,
    val background: Color? = null,
    val fontSize: TextUnit? = null,
    val fontWeight: FontWeight? = null,
    val fontStyle: FontStyle? = null,
    val textAlign: TextAlign? = null,
) {
    val isEmpty: Boolean
        get() = color == null && background == null && fontSize == null &&
            fontWeight == null && fontStyle == null && textAlign == null
}

/** 清洗结果；[nodes] 为 null 表示病态输入（超深或超节点数），调用方应回落源码。 */
internal class SanitizedHtml(val nodes: List<HtmlNode>?)

/**
 * HTML 片段 → [HtmlNode] 树。
 *
 * 解析用 ksoup（jsoup 的 KMP 移植，行为一致）：LLM 输出经常缺闭合标签，
 * 只有真正的解析器能容错，正则不行。两个必须显式的安全点：
 * 1. 解析器**原样保留** `class` / `onclick` 等属性 → 这里按白名单过滤；
 * 2. `script` / `style` / `noscript` 节点会被保留 → 直接丢弃整棵子树。
 */
internal object HtmlSanitizer {

    /** 最大嵌套深度（超过整块回落源码，不做截断渲染，避免"半个页面"）。 */
    const val MAX_DEPTH: Int = 32

    /** 最大节点数（含文本节点）。 */
    const val MAX_NODES: Int = 5_000

    fun sanitize(html: String): SanitizedHtml {
        val body = runCatching { Ksoup.parseBodyFragment(html).body() }.getOrNull()
            ?: return SanitizedHtml(emptyList())
        val counter = Counter()
        val nodes = sanitizeChildren(body, 0, counter)
        if (counter.exceeded) return SanitizedHtml(null)
        return SanitizedHtml(nodes)
    }

    private class Counter {
        var count: Int = 0
        var exceeded: Boolean = false
    }

    private fun sanitizeChildren(parent: Element, depth: Int, counter: Counter): List<HtmlNode> {
        val out = mutableListOf<HtmlNode>()
        parent.childNodes().forEach { child ->
            when (child) {
                is TextNode -> {
                    counter.count++
                    if (counter.count > MAX_NODES) counter.exceeded = true
                    // `wholeText` 而不是 `text()`：后者会折叠空白，`<pre>` 里的缩进与换行会被吃掉
                    val text = child.getWholeText()
                    if (text.isNotEmpty()) out += HtmlNode.Text(text)
                }

                is Element -> out += sanitizeElement(child, depth, counter)
                else -> Unit
            }
        }
        return out
    }

    private fun sanitizeElement(element: Element, depth: Int, counter: Counter): List<HtmlNode> {
        counter.count++
        if (counter.count > MAX_NODES || depth > MAX_DEPTH) {
            counter.exceeded = true
            return emptyList()
        }
        val tag = element.tagName().lowercase()
        if (tag in DROPPED_TAGS) return emptyList()

        if (tag !in ALLOWED_TAGS) {
            // 不在白名单：剥壳保留内容（GitHub 的行为），不能连正文一起吞掉
            return sanitizeChildren(element, depth + 1, counter)
        }
        if (tag in VOID_TAGS) {
            return listOf(HtmlNode.Element(tag, allowedAttrs(tag, element), parseStyle(element), emptyList()))
        }
        val children = sanitizeChildren(element, depth + 1, counter)
        return listOf(HtmlNode.Element(tag, allowedAttrs(tag, element), parseStyle(element), children))
    }

    private fun allowedAttrs(tag: String, element: Element): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        element.attributes().asList().forEach { attr ->
            val name = attr.attributeKey.lowercase()
            if (!isAllowedAttr(tag, name)) return@forEach
            out[name] = attr.attributeValue ?: ""
        }
        return out
    }

    private fun isAllowedAttr(tag: String, name: String): Boolean {
        // `on*` 是脚本入口，`class` 没有语义（我们没有样式表），一律丢弃
        if (name.startsWith("on") || name == "class") return false
        if (name == "style") return false // style 走 parseStyle，不进属性表
        if (name.startsWith("aria-") || name in GLOBAL_ATTRS) return true
        return name in perTagAttrs(tag)
    }

    private fun perTagAttrs(tag: String): Set<String> = when (tag) {
        "a" -> setOf("href", "title", "target", "rel")
        "img" -> setOf("src", "alt", "width", "height", "title", "loading")
        "source" -> setOf("src", "srcset", "type", "media")
        "td", "th" -> setOf("colspan", "rowspan", "align", "valign", "scope", "headers")
        "ol" -> setOf("start", "type", "reversed")
        "details" -> setOf("open")
        else -> emptySet()
    }

    private fun parseStyle(element: Element): HtmlStyle {
        val raw = element.attr("style")
        if (raw.isBlank()) return HtmlStyle()
        var style = HtmlStyle()
        raw.split(';').forEach { declaration ->
            val colon = declaration.indexOf(':')
            if (colon <= 0) return@forEach
            val name = declaration.substring(0, colon).trim().lowercase()
            val value = declaration.substring(colon + 1).trim()
            if (value.isEmpty()) return@forEach
            style = when (name) {
                "color" -> style.copy(color = parseCssColor(value) ?: style.color)
                "background-color" -> style.copy(background = parseCssColor(value) ?: style.background)
                "font-size" -> style.copy(fontSize = parseCssSize(value) ?: style.fontSize)
                "font-weight" -> style.copy(fontWeight = parseFontWeight(value) ?: style.fontWeight)
                "font-style" -> style.copy(
                    fontStyle = when (value.lowercase()) {
                        "italic", "oblique" -> FontStyle.Italic
                        "normal" -> FontStyle.Normal
                        else -> style.fontStyle
                    },
                )

                "text-align" -> style.copy(
                    textAlign = when (value.lowercase()) {
                        "center" -> TextAlign.Center
                        "right" -> TextAlign.End
                        "left", "start" -> TextAlign.Start
                        else -> style.textAlign
                    },
                )
                // 布局级属性（display / flex / position / margin / padding …）静默忽略
                else -> style
            }
        }
        return style
    }

    /** 只支持纯数字（px / pt / em 的倍率按 16sp 折算），百分比一律忽略。 */
    private fun parseCssSize(value: String): TextUnit? {
        val text = value.trim().lowercase()
        return when {
            text.endsWith("px") -> text.removeSuffix("px").trim().toFloatOrNull()?.takeIf { it > 0 }?.sp
            text.endsWith("pt") -> text.removeSuffix("pt").trim().toFloatOrNull()?.takeIf { it > 0 }?.times(4f / 3f)?.sp
            text.endsWith("em") -> text.removeSuffix("em").trim().toFloatOrNull()?.takeIf { it > 0 }?.times(16f)?.sp
            else -> null
        }
    }

    private fun parseFontWeight(value: String): FontWeight? = when (value.trim().lowercase()) {
        "bold", "bolder", "700", "800", "900" -> FontWeight.Bold
        "normal", "400" -> FontWeight.Normal
        "300", "lighter" -> FontWeight.Light
        else -> null
    }
}

/** `#rgb` / `#rrggbb` / `rgb(r,g,b)` / 少量命名色。解析不了就忽略（不猜）。 */
internal fun parseCssColor(value: String): Color? {
    val text = value.trim().lowercase()
    NAMED_COLORS[text]?.let { return it }
    if (text.startsWith("#")) {
        val hex = text.removePrefix("#")
        return when (hex.length) {
            3 -> {
                val r = hex[0].digitToIntOrNull(16) ?: return null
                val g = hex[1].digitToIntOrNull(16) ?: return null
                val b = hex[2].digitToIntOrNull(16) ?: return null
                Color(red = r * 17, green = g * 17, blue = b * 17)
            }

            6 -> hex.toLongOrNull(16)?.let { Color(0xFF000000 or it) }
            8 -> hex.toLongOrNull(16)?.let { Color(it) }
            else -> null
        }
    }
    if (text.startsWith("rgb")) {
        val inner = text.substringAfter('(').substringBefore(')')
        val parts = inner.split(',').map { it.trim() }
        if (parts.size < 3) return null
        val r = parts[0].toFloatOrNull()?.toInt()?.coerceIn(0, 255) ?: return null
        val g = parts[1].toFloatOrNull()?.toInt()?.coerceIn(0, 255) ?: return null
        val b = parts[2].toFloatOrNull()?.toInt()?.coerceIn(0, 255) ?: return null
        return Color(r, g, b)
    }
    return null
}

private val NAMED_COLORS = mapOf(
    "black" to Color(0xFF000000),
    "white" to Color(0xFFFFFFFF),
    "red" to Color(0xFFD32F2F),
    "green" to Color(0xFF2E7D32),
    "blue" to Color(0xFF1565C0),
    "orange" to Color(0xFFEF6C00),
    "yellow" to Color(0xFFF9A825),
    "purple" to Color(0xFF6A1B9A),
    "gray" to Color(0xFF757575),
    "grey" to Color(0xFF757575),
)

/**
 * 元素白名单：GitHub sanitizer 的 60 个标签。
 *
 * 比 GitHub 略窄 —— `picture` / `source` 这类响应式图片容器没有图片源就没有意义，
 * 保留反而会出现空块。
 */
internal val ALLOWED_TAGS: Set<String> = setOf(
    "h1", "h2", "h3", "h4", "h5", "h6", "br", "b", "i", "strong", "em", "a", "pre", "code",
    "img", "tt", "div", "ins", "del", "sup", "sub", "p", "ol", "ul", "table", "thead", "tbody",
    "tfoot", "blockquote", "dl", "dt", "dd", "kbd", "q", "samp", "var", "hr", "ruby", "rt", "rp",
    "li", "tr", "td", "th", "s", "strike", "summary", "details", "caption", "figure",
    "figcaption", "abbr", "bdo", "cite", "dfn", "mark", "small", "span", "time", "wbr",
)

/** 整棵子树丢弃的标签（与 GFM 的 tagfilter 一致）。 */
internal val DROPPED_TAGS: Set<String> = setOf(
    "script", "style", "noscript", "iframe", "object", "embed", "template", "svg",
    "form", "input", "button", "select", "textarea", "link", "meta", "head", "title",
)

internal val VOID_TAGS: Set<String> = setOf("br", "hr", "img", "wbr")

/** 所有元素都允许的属性（GitHub `all` 列表的子集）。 */
internal val GLOBAL_ATTRS: Set<String> = setOf(
    "align", "width", "height", "border", "valign", "colspan", "rowspan", "start", "type",
    "open", "title", "id", "dir", "lang", "scope", "headers", "span", "size", "alt", "src",
    "href", "srcset", "loading", "cite", "datetime", "tabindex", "role", "itemprop",
    "itemscope", "itemtype",
)
