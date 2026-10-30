package io.github.shinglem.markdown.parse

import io.github.shinglem.markdown.collectInlineTexes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 行内公式的解析约定。
 *
 * GFM 原生把 `$x$` 解析成 `INLINE_MATH` 节点（不是普通文本），所以 tex 只能从 AST 收集 ——
 * 收集与渲染必须用同一份字符串，否则 inline content 的 key 对不上，公式会退化成源码。
 */
class InlineMathTest {

    private val parser = GfmMarkdownParser()

    @Test
    fun `行内公式被解析成 INLINE_MATH 节点`() {
        val block = parser.parse("设 \$x\$ 为实数。", streaming = false).blocks.single()
        val types = block.node.children.flatMap { it.children + it }.map { it.type.toString() }
        assertTrue(types.any { it.contains("INLINE_MATH") }, "AST 里应有 INLINE_MATH，实际=$types")
    }

    @Test
    fun `从 AST 收集 tex`() {
        val document = parser.parse("设 \$x\$ 与 \$y^2\$，且 \$x = 0\$。", streaming = false)
        val source = document.blocks.first().source
        assertEquals(setOf("x", "y^2", "x = 0"), collectInlineTexes(document.blocks, source))
    }

    @Test
    fun `块级公式不进内联集合`() {
        val document = parser.parse("$$\nE = mc^2\n$$\n", streaming = false)
        val source = document.blocks.first().source
        assertEquals(emptySet(), collectInlineTexes(document.blocks, source))
    }

    @Test
    fun `没有公式时集合为空`() {
        val document = parser.parse("普通段落，价格 \$100。", streaming = false)
        val source = document.blocks.first().source
        assertEquals(emptySet(), collectInlineTexes(document.blocks, source))
    }
}
