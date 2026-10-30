package io.github.shinglem.markdown.parse

import io.github.shinglem.markdown.adapter.Formats
import io.github.shinglem.markdown.model.MdBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 块结构的解析约定。这些结构一旦被误判，渲染层会静默出错（例如 setext 变成空标题、
 * 任务列表丢掉 checkbox），所以单独钉住。
 */
class BlockStructureTest {

    private val parser = GfmMarkdownParser()

    @Test
    fun `任务列表解析为无序列表块`() {
        val block = parser.parse("- [x] 已完成\n- [ ] 未完成\n", streaming = false).blocks.single()
        assertTrue(block is MdBlock.ListBlock)
        assertEquals(false, block.ordered)
    }

    @Test
    fun `任务列表的 checkbox 在 AST 里存在`() {
        val block = parser.parse("- [x] 已完成\n", streaming = false).blocks.single() as MdBlock.ListBlock
        val item = block.node.children.first()
        val types = item.children.map { it.type.toString() }
        assertTrue(types.any { it.contains("CHECK_BOX") }, "AST 里应有 CHECK_BOX，实际=$types")
    }

    @Test
    fun `setext 标题识别为对应级别`() {
        val blocks = parser.parse("标题\n---\n\n正文\n", streaming = false).blocks
        val heading = blocks.first() as MdBlock.Heading
        assertEquals(2, heading.level)
        // 内容挂在 SETEXT_CONTENT 下，渲染时必须走 setextHeading*
        assertTrue(heading.node.type.toString().contains("SETEXT"))
    }

    @Test
    fun `GFM 原生识别块级数学公式`() {
        val block = parser.parse("$$\nE = mc^2\n$$\n", streaming = false).blocks.single()
        assertTrue(block is MdBlock.Math)
        assertEquals("E = mc^2", block.tex)
    }

    @Test
    fun `块级 HTML 单独成块`() {
        val block = parser.parse("<div align=\"center\">hi</div>\n", streaming = false).blocks.single()
        assertTrue(block is MdBlock.Html)
    }

    @Test
    fun `嵌套引用仍是单个引用块`() {
        val blocks = parser.parse("> 一层\n>\n> > 嵌套\n", streaming = false).blocks
        assertEquals(1, blocks.count { it is MdBlock.Quote })
    }

    @Test
    fun `表格识别为 table 块`() {
        val block = parser.parse("| a | b |\n| --- | --- |\n| 1 | 2 |\n", streaming = false).blocks.single()
        assertTrue(block is MdBlock.Table)
    }

    @Test
    fun `表格块同时带 markdown 与 table 格式`() {
        // 带 `table` 是给 BlockViewportConfig 用的：表格仍然由 markdown 适配器渲染。
        val block = parser.parse("| a | b |\n| --- | --- |\n| 1 | 2 |\n", streaming = false).blocks.single()
        assertEquals(setOf(Formats.Markdown, Formats.Table), block.formats)
    }

    @Test
    fun `不同文档的块 key 会重复但内容不同`() {
        val a = parser.parse("# 标题 A\n", streaming = false).blocks.single()
        val b = parser.parse("# 标题 B\n", streaming = false).blocks.single()
        assertEquals(a.key, b.key)
        assertTrue(a.raw != b.raw)
    }
}
