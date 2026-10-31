package io.github.shinglem.markdown.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import io.github.shinglem.markdown.MATH_KEY_PREFIX
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 行内构建器的行为约定。
 *
 * `buildMdInline` 是纯函数（样式从参数进、`AnnotatedString` 出），所以这些用例不需要
 * 起 Compose 组合 —— 它们锁的是「文本不丢、不重复、样式落在正确区间」。
 */
class MdInlineTest {

    private val parser = MarkdownParser(GFMFlavourDescriptor())

    private val styles = MdInlineStyles(
        link = TextLinkStyles(SpanStyle(color = Color.Blue)),
        code = SpanStyle(fontFamily = FontFamily.Monospace),
        highlight = SpanStyle(background = Color.Yellow),
        references = mapOf("ref" to "https://example.com/ref"),
        linkListener = null,
    )

    @Test
    fun `粗体与斜体落到正确区间`() {
        val result = inline("a **b** *c* d")
        assertEquals("a b c d", result.text.text)

        assertTrue(hasWeight(result, 2, FontWeight.Bold), "b 应为粗体")
        assertTrue(hasItalic(result, 4), "c 应为斜体")
        assertTrue(!hasAnySpan(result, 0), "a 不应带样式")
    }

    @Test
    fun `嵌套强调内外层样式都生效`() {
        val result = inline("**a *b* c**")
        assertEquals("a b c", result.text.text)

        assertTrue(hasWeight(result, 0, FontWeight.Bold), "外层整段应为粗体")
        assertTrue(hasItalic(result, 2), "内层应为斜体")
        assertTrue(hasWeight(result, 2, FontWeight.Bold), "内层仍应保留外层粗体")
    }

    @Test
    fun `删除线与高亮`() {
        val strike = inline("a ~~b~~ c")
        assertEquals("a b c", strike.text.text)
        assertTrue(
            strike.text.spanStyles.any {
                it.start <= 2 && 2 < it.end && it.item.textDecoration == TextDecoration.LineThrough
            },
            "b 应有删除线",
        )

        val highlight = inline("a ==b== c")
        assertEquals("a b c", highlight.text.text)
        assertTrue(
            highlight.text.spanStyles.any {
                it.start <= 2 && 2 < it.end && it.item.background != Color.Unspecified
            },
            "b 应有高亮背景",
        )
    }

    @Test
    fun `行内代码保持等宽且内部的美元符号不算公式`() {
        val result = inline("价格 `\$100` 与 \$y\$")
        // 行内代码两侧各补一个空格，是给等宽背景留出的内边距（与旧实现一致）。
        assertEquals("价格  \$100  与 y", result.text.text)

        assertTrue(
            result.text.spanStyles.any {
                it.start <= 3 && 3 < it.end && it.item.fontFamily == FontFamily.Monospace
            },
            "`\$100` 应为等宽",
        )
        assertEquals(
            1,
            result.text.getStringAnnotations(0, result.text.length).count { it.item.startsWith(MATH_KEY_PREFIX) },
            "只有 \$y\$ 应成为公式",
        )
    }

    @Test
    fun `行内链接带地址且不显示方括号`() {
        val result = inline("见 [文档](https://example.com/a) 末")
        assertEquals("见 文档 末", result.text.text)

        val url = result.text.getLinkAnnotations(0, result.text.length)
            .firstOrNull()?.item as? LinkAnnotation.Url
        assertEquals("https://example.com/a", url?.url)
    }

    @Test
    fun `引用式链接按全文定义解析`() {
        // 走真实路径：先扫全文收集 `[label]: url` 定义，再构建行内文本。
        // 注意 key 保留方括号（`[ref]`）—— 定义侧与使用侧用的是同一套取法。
        val markdown = "见 [文档][ref] 末\n\n[ref]: https://example.com/ref"
        val tree = parser.buildMarkdownTreeFromString(markdown as CharSequence)
        val references = collectLinkDefinitions(markdown, tree.children)
        val paragraph = tree.children.first { it.type == MarkdownElementTypes.PARAGRAPH }

        val result = buildMdInline(markdown, paragraph, styles.copy(references = references))
        assertEquals("见 文档 末", result.text.text)

        val url = result.text.getLinkAnnotations(0, result.text.length)
            .firstOrNull()?.item as? LinkAnnotation.Url
        assertEquals("https://example.com/ref", url?.url)
    }

    @Test
    fun `未定义的引用式链接按源码显示而不是消失`() {
        val result = inline("见 [文档][nope] 末")
        assertTrue(
            result.text.text.contains("[文档][nope]"),
            "缺定义时应保留源码，实际=${result.text.text}",
        )
    }

    @Test
    fun `转义的星号按字面显示`() {
        val result = inline("a \\*b\\* c")
        assertEquals("a *b* c", result.text.text)
    }

    @Test
    fun `行内公式以 inline content 出现`() {
        val result = inline("设 \$x\$ 为实数")
        assertEquals("设 x 为实数", result.text.text)
        assertEquals(
            1,
            result.text.getStringAnnotations(0, result.text.length).count { it.item.startsWith(MATH_KEY_PREFIX) },
            "公式应登记成 $MATH_KEY_PREFIX 前缀的 inline content",
        )
    }

    @Test
    fun `图片登记到 images 且不把源码漏进文本`() {
        val result = inline("前面 ![替代文字](https://example.com/a.png) 后面")
        assertEquals(1, result.images.size, "应登记 1 张图片")
        assertEquals("https://example.com/a.png", result.images.single().url)
        assertEquals("替代文字", result.images.single().alt)
        assertTrue(!result.text.text.contains("<img"), "不应把 HTML 源码漏进文本")
    }

    @Test
    fun `软换行合并为空格`() {
        val result = inline("第一行\n第二行")
        assertEquals("第一行 第二行", result.text.text)
    }

    @Test
    fun `反斜杠硬换行保留换行`() {
        val result = inline("第一行\\\n第二行")
        assertTrue(result.text.text.contains('\n'), "硬换行应保留换行符，实际=${result.text.text}")
    }

    private fun inline(markdown: String): MdInline {
        val tree = parser.buildMarkdownTreeFromString(markdown as CharSequence)
        val paragraph = tree.children.firstOrNull { it.type == MarkdownElementTypes.PARAGRAPH }
            ?: tree.children.first()
        return buildMdInline(markdown, paragraph, styles)
    }

    private fun hasWeight(result: MdInline, index: Int, weight: FontWeight): Boolean =
        result.text.spanStyles.any { it.start <= index && index < it.end && it.item.fontWeight == weight }

    private fun hasItalic(result: MdInline, index: Int): Boolean =
        result.text.spanStyles.any { it.start <= index && index < it.end && it.item.fontStyle == FontStyle.Italic }

    private fun hasAnySpan(result: MdInline, index: Int): Boolean =
        result.text.spanStyles.any { it.start <= index && index < it.end }
}
