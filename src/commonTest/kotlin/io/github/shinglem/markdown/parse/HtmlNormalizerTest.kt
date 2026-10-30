package io.github.shinglem.markdown.parse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 内联 HTML 的改写规则。
 *
 * 这些标签如果原样交给解析器，会被当成 `HTML_INLINE` 叶子吞掉（只剩文字），
 * 或者整段显示成源码；改写成等价 Markdown 标记后，样式/图片/换行才能正常渲染。
 */
class HtmlNormalizerTest {

    @Test
    fun `粗体与行内代码标签转成 markdown 标记`() {
        assertEquals(
            "段落里有 **粗体** 与 `码`",
            normalizeInlineHtml("段落里有 <b>粗体</b> 与 <code>码</code>"),
        )
    }

    @Test
    fun `斜体与删除线标签转成 markdown 标记`() {
        assertEquals(
            "*斜* 与 ~~删~~",
            normalizeInlineHtml("<em>斜</em> 与 <del>删</del>"),
        )
    }

    @Test
    fun `br 转成硬换行`() {
        assertEquals("第一行  \n第二行", normalizeInlineHtml("第一行<br>第二行"))
        assertEquals("第一行  \n第二行", normalizeInlineHtml("第一行<br/>第二行"))
        assertEquals("第一行  \n第二行", normalizeInlineHtml("第一行<BR />第二行"))
    }

    @Test
    fun `img 转成 markdown 图片`() {
        assertEquals(
            "![badge](https://example.com/a.svg)",
            normalizeInlineHtml("""<img src="https://example.com/a.svg" alt="badge">"""),
        )
    }

    @Test
    fun `a 标签转成链接`() {
        assertEquals(
            "见 [文档](https://example.com/d)",
            normalizeInlineHtml("""见 <a href="https://example.com/d">文档</a>"""),
        )
    }

    @Test
    fun `围栏代码块里的标签不动`() {
        val source = "```html\n<b>原样</b>\n```\n"
        assertEquals(source, normalizeInlineHtml(source))
    }

    @Test
    fun `行内代码里的标签不动`() {
        assertEquals("用 `<b>` 加粗", normalizeInlineHtml("用 `<b>` 加粗"))
    }

    @Test
    fun `白名单以外的标签原样保留`() {
        // 避免把正文里的比较符或自定义标签吃掉。
        assertEquals("<span>x</span> 与 a<b>c", normalizeInlineHtml("<span>x</span> 与 a<b>c"))
    }

    @Test
    fun `HTML 表格转成 markdown 表格`() {
        val source = """
            <table>
            <tr><th>原生 HTML 表格</th><th>是否渲染</th></tr>
            <tr><td>inline 标签</td><td>通常支持</td></tr>
            </table>
        """.trimIndent()
        val expected = """

            | 原生 HTML 表格 | 是否渲染 |
            | --- | --- |
            | inline 标签 | 通常支持 |

        """.trimIndent()
        assertTrue(normalizeInlineHtml(source).contains(expected), normalizeInlineHtml(source))
    }
}
