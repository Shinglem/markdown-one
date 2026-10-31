package io.github.shinglem.markdown.parse

import io.github.shinglem.markdown.collectInlineTexes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 行内公式的解析约定。
 *
 * GFM 原生把 `$x$` 解析成 `INLINE_MATH` 节点（不是普通文本），所以 tex 只能从 AST 收集 ——
 * 收集与渲染必须用同一份字符串，否则 inline content 的 key 对不上，公式会退化成源码。
 *
 * **识别口径与上游 0.7.17 保持一致：`$` 前必须是空白或行首**（`canOpenMath`）。
 * 紧贴中文标点/汉字的 `$…$` 不渲染公式，按源码显示 —— 我们刻意不做补偿。
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
    fun `只有前导空白的公式被收集`() {
        // 第一个公式前是空格 → INLINE_MATH；第二个紧贴中文逗号 → 0.7.17 不认，保留成 DOLLAR。
        val document = parser.parse("行内: 当 \$n \\to \\infty\$ 时，\$\\bar{x} \\to \\mu\$。", streaming = false)
        val source = document.blocks.first().source
        assertEquals(setOf("n \\to \\infty"), collectInlineTexes(document.blocks, source))
    }

    @Test
    fun `紧贴标点的公式保留成普通美元符`() {
        val block = parser.parse("时，\$\\bar{x}\$。", streaming = false).blocks.single()
        val types = flatten(block.node).map { it.type.toString() }
        assertFalse(types.any { it.contains("INLINE_MATH") }, "0.7.17 要求前导空白，实际=$types")
        assertEquals(2, types.count { it.contains("DOLLAR") }, "两个美元符都应保留，实际=$types")
    }

    @Test
    fun `块级公式不进内联集合`() {
        val document = parser.parse("$$\nE = mc^2\n$$\n", streaming = false)
        val source = document.blocks.first().source
        assertEquals(emptySet(), collectInlineTexes(document.blocks, source))
    }

    @Test
    fun `行内代码里的美元符号不算公式`() {
        // CODE_SPAN 的内容是 TEXT 而不是 INLINE_MATH；annotator 也不在 TEXT 上兜底扫描，
        // 否则 `` `$x$` `` 会被当成公式渲染。
        val document = parser.parse("行内`\$x\$`代码与 \$y\$。", streaming = false)
        val source = document.blocks.first().source
        assertEquals(setOf("y"), collectInlineTexes(document.blocks, source))
    }

    @Test
    fun `没有公式时集合为空`() {
        val document = parser.parse("普通段落，价格 \$100。", streaming = false)
        val source = document.blocks.first().source
        assertEquals(emptySet(), collectInlineTexes(document.blocks, source))
    }

    private fun flatten(node: org.intellij.markdown.ast.ASTNode): List<org.intellij.markdown.ast.ASTNode> =
        node.children.flatMap { listOf(it) + flatten(it) }
}
