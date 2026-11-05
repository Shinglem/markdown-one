package io.github.shinglem.markdown.builtin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import io.github.shinglem.markdown.parse.HtmlSanitizer
import io.github.shinglem.markdown.text.HtmlInlineStyles
import io.github.shinglem.markdown.text.buildHtmlInline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * HTML 行内构建：标签语义 → `AnnotatedString` 的 span。
 *
 * 只验「理解对了」的部分（样式落在哪个区间、图片有没有占位），布局与观感不在单测范围内。
 */
class HtmlInlineTest {

    private val styles = HtmlInlineStyles(
        link = TextLinkStyles(SpanStyle(color = Color.Blue)),
        code = SpanStyle(color = Color.Magenta),
        mark = SpanStyle(background = Color.Yellow),
        linkListener = null,
    )

    private fun inline(html: String) = buildHtmlInline(HtmlSanitizer.sanitize(html).nodes!!, styles)

    @Test
    fun `粗体斜体删除线落到正确 span`() {
        val result = inline("<b>粗</b><i>斜</i><s>删</s>")
        assertEquals("粗斜删", result.text.text)
        assertTrue(result.text.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(result.text.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
        assertTrue(result.text.spanStyles.any { it.item.textDecoration == TextDecoration.LineThrough })
    }

    @Test
    fun `上下标使用 baselineShift`() {
        val result = inline("H<sub>2</sub>O 与 x<sup>2</sup>")
        val shifts = result.text.spanStyles.map { it.item.baselineShift }
        assertTrue(BaselineShift.Subscript in shifts)
        assertTrue(BaselineShift.Superscript in shifts)
    }

    @Test
    fun `br 变成换行`() {
        assertEquals("甲\n乙", inline("甲<br>乙").text.text)
    }

    @Test
    fun `html 语义下连续空白折叠成一个空格`() {
        assertEquals("甲 乙", inline("<p>甲   \n  乙</p>").text.text)
    }

    @Test
    fun `链接保留 url`() {
        val result = inline("""见 <a href="https://example.com/x">文档</a>""")
        assertEquals("见 文档", result.text.text)
        assertTrue(result.text.getLinkAnnotations(0, result.text.length).isNotEmpty())
    }

    @Test
    fun `图片变成 inline content 并透传尺寸`() {
        val result = inline("""<img src="a.svg" alt="图" width="120" height="40">""")
        assertEquals(1, result.images.size)
        val image = result.images.single()
        assertEquals("a.svg", image.src)
        assertEquals(120, image.widthPx)
        assertEquals(40, image.heightPx)
        // 文本里留下的是 inline content 的替代文本（图片加载不出来时显示它）
        assertEquals("图", result.text.text)
    }

    @Test
    fun `style 里的颜色落到 span`() {
        val result = inline("""<span style="color:#ff0000">红</span>""")
        assertEquals("红", result.text.text)
        assertEquals(Color(0xFFFF0000), result.text.spanStyles.single().item.color)
    }

    @Test
    fun `行内与块级节点会被分组`() {
        val nodes = HtmlSanitizer.sanitize("前<p>段</p>后").nodes!!
        val runs = groupHtmlRuns(nodes)
        assertEquals(3, runs.size)
        assertTrue(runs[0] is HtmlRun.Inline)
        assertTrue(runs[1] is HtmlRun.Block)
        assertTrue(runs[2] is HtmlRun.Inline)
    }

    @Test
    fun `连续的行内节点合成一段`() {
        val nodes = HtmlSanitizer.sanitize("<b>甲</b><i>乙</i><span>丙</span>").nodes!!
        val runs = groupHtmlRuns(nodes)
        assertEquals(1, runs.size)
        assertTrue(runs.single() is HtmlRun.Inline)
    }

    @Test
    fun `pre 的文本内容被完整取出`() {
        val pre = HtmlSanitizer.sanitize("<pre><code>val x = 1\nval y = 2</code></pre>").nodes!!
            .single() as io.github.shinglem.markdown.parse.HtmlNode.Element
        assertEquals("val x = 1\nval y = 2", textContent(pre))
    }
}
