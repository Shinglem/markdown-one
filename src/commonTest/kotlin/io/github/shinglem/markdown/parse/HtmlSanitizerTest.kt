package io.github.shinglem.markdown.parse

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.shinglem.markdown.builtin.textContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * HTML 白名单清洗。
 *
 * 依据是 GitHub `html-pipeline` 的 sanitization 配置：元素/属性白名单、丢弃 `script` 子树、
 * 没有 `class` 与 `on*`。这里钉住的都是**安全**相关的判定，不是渲染效果。
 */
class HtmlSanitizerTest {

    private fun sanitize(html: String): List<HtmlNode> = HtmlSanitizer.sanitize(html).nodes.orEmpty()

    private fun nullableNodes(html: String): List<HtmlNode>? = HtmlSanitizer.sanitize(html).nodes

    @Test
    fun `脚本与样式子树整体丢弃`() {
        val nodes = assertNotNull(sanitize("<div>a<script>alert(1)</script>b<style>.x{}</style>c</div>"))
        assertEquals("abc", nodes.joinToString("") { textContent(it) })
    }

    @Test
    fun `事件属性与 class 被丢弃`() {
        val div = sanitize("""<div onclick="evil()" class="x" title="t">a</div>""").single() as HtmlNode.Element
        assertEquals(setOf("title"), div.attrs.keys)
        assertEquals("t", div.attrs["title"])
    }

    @Test
    fun `style 只解析能映射到 TextStyle 的六类属性`() {
        val span = sanitize(
            """<span style="color:#f00;background-color:#0f0;font-size:20px;font-weight:bold;font-style:italic;text-align:center;display:flex;margin:10px">x</span>""",
        ).single() as HtmlNode.Element
        val style = span.style
        assertEquals(Color(0xFFFF0000), style.color)
        assertEquals(Color(0xFF00FF00), style.background)
        assertEquals(20.sp, style.fontSize)
        assertEquals(FontWeight.Bold, style.fontWeight)
        assertEquals(FontStyle.Italic, style.fontStyle)
        assertNotNull(style.textAlign)
        // display / margin 这些布局属性压根不进模型
        assertTrue("display" !in span.attrs && "margin" !in span.attrs)
    }

    @Test
    fun `布局级 style 全部忽略时 style 为空`() {
        val span = sanitize("""<span style="display:flex;position:absolute;float:left">x</span>""")
            .single() as HtmlNode.Element
        assertTrue(span.style.isEmpty, span.style.toString())
    }

    @Test
    fun `白名单之外的标签剥壳保留内容`() {
        val nodes = assertNotNull(sanitize("<custom-tag>内容</custom-tag>"))
        assertEquals("内容", nodes.joinToString("") { textContent(it) })
        assertTrue(nodes.all { it !is HtmlNode.Element })
    }

    @Test
    fun `未闭合的标签由解析器补全`() {
        val div = sanitize("<div><p>text").single() as HtmlNode.Element
        assertEquals("div", div.tag)
        val p = div.children.single() as HtmlNode.Element
        assertEquals("p", p.tag)
        assertEquals("text", textContent(p))
    }

    @Test
    fun `img 的尺寸属性保留`() {
        val img = sanitize("""<img src="a.svg" alt="图" width="120" height="40">""")
            .single() as HtmlNode.Element
        assertEquals("a.svg", img.attrs["src"])
        assertEquals("120", img.attrs["width"])
        assertEquals("40", img.attrs["height"])
    }

    @Test
    fun `超过深度上限整块返回 null`() {
        val html = "<div>".repeat(40) + "x" + "</div>".repeat(40)
        assertNull(nullableNodes(html))
    }

    @Test
    fun `超过节点上限整块返回 null`() {
        val html = "<p>x</p>".repeat(3_000)
        assertNull(nullableNodes(html))
    }

    @Test
    fun `正常文档不受防护影响`() {
        val nodes = assertNotNull(sanitize("<h2>标题</h2><ul><li>甲</li><li>乙</li></ul>"))
        assertEquals(2, nodes.size)
        assertTrue(nodes[0] is HtmlNode.Element && (nodes[0] as HtmlNode.Element).tag == "h2")
    }

    @Test
    fun `1000 节点解析在门槛内`() {
        // 判据来自 `.design/待修复问题.md` #9：1000 节点解析 < 80ms（ksoup 实测 56ms）。
        // 这里留一点余量给 CI 的慢机器，但门槛仍远低于「卡顿」的量级。
        val html = (1..1_000).joinToString("") { "<p>段落 $it</p>" }
        // 预热一次，避免把类加载时间算进去
        HtmlSanitizer.sanitize(html)
        val started = System.nanoTime()
        val nodes = assertNotNull(nullableNodes(html))
        val elapsedMs = (System.nanoTime() - started) / 1_000_000.0
        println("sanitize 1000 节点耗时 ${elapsedMs}ms，输出 ${nodes.size} 个节点")
        assertEquals(1_000, nodes.size)
        assertTrue(elapsedMs < 200.0, "解析 1000 节点耗时 ${elapsedMs}ms，超出门槛")
    }
}
