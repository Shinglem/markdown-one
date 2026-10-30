package io.github.shinglem.markdown.parse

import io.github.shinglem.markdown.model.MdBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamingParseTest {

    private val parser = GfmMarkdownParser()

    @Test
    fun `未闭合的代码栅栏在流式下被补齐`() {
        val partial = "正文\n\n```kotlin\nval x = 1\n"
        assertEquals(partial + "```", balanceCodeFences(partial))
    }

    @Test
    fun `已闭合的栅栏不再改动`() {
        val complete = "```kotlin\nval x = 1\n```\n"
        assertEquals(complete, balanceCodeFences(complete))
    }

    @Test
    fun `行内反引号不计入栅栏`() {
        val text = "这是 `inline` 代码，不是栅栏。\n"
        assertEquals(text, balanceCodeFences(text))
    }

    @Test
    fun `补齐后能解析出代码块`() {
        val doc = parser.parse("```kotlin\nval x = 1\n", streaming = true)
        assertTrue(doc.blocks.any { it is MdBlock.CodeBlock })
    }

    @Test
    fun `代码块语言名被正确提取`() {
        val block = parser.parse("```json\n{}\n```", streaming = false).blocks.single() as MdBlock.CodeBlock
        assertEquals("json", block.language)
        assertEquals("{}", block.code)
    }

    @Test
    fun `整段公式识别为 math 块`() {
        val block = parser.parse("$$\nE = mc^2\n$$", streaming = false).blocks.single()
        assertTrue(block is MdBlock.Math)
        assertEquals("E = mc^2", block.tex)
    }

    @Test
    fun `标题级别被正确识别`() {
        val blocks = parser.parse("# 一级\n\n### 三级\n", streaming = false).blocks
        assertEquals(1, (blocks[0] as MdBlock.Heading).level)
        assertEquals(3, (blocks[1] as MdBlock.Heading).level)
    }
}
