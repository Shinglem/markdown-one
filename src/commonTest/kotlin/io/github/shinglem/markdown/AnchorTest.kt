package io.github.shinglem.markdown

import io.github.shinglem.markdown.text.headingAnchor
import io.github.shinglem.markdown.text.isAnchorLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 文内跳转：`#标题` 与 `#fn-xxx`。
 *
 * 以前这类链接被当成普通外链交给宿主 `openExternalUrl`，于是「点脚注序号没反应」、
 * 「点 AI 生成的目录没反应」—— 两个都是实测过的缺陷。
 */
class AnchorTest {

    @Test
    fun `英文标题按 GitHub 规则 slug 化`() {
        assertEquals("quick-start", headingAnchor("Quick Start"))
        assertEquals("api-reference", headingAnchor("API Reference"))
    }

    @Test
    fun `中文标题保留中文`() {
        assertEquals("快速开始", headingAnchor("快速开始"))
        // `、` 是标点，直接丢掉（GitHub 同此行为），不会变成连字符
        assertEquals("一准备工作", headingAnchor("一、准备工作"))
    }

    @Test
    fun `标点与行内标记被丢掉`() {
        // 反引号丢掉、中间的空格仍是一个连字符
        assertEquals("安装-npm", headingAnchor("安装 `npm`"))
        assertEquals("是什么", headingAnchor("**是什么？**"))
        assertEquals("a-b", headingAnchor("a / b"))
    }

    @Test
    fun `连续空白压成一个连字符`() {
        assertEquals("a-b", headingAnchor("a    b"))
        assertEquals("a-b", headingAnchor("  a  b  "))
    }

    @Test
    fun `片段链接判定`() {
        assertTrue(isAnchorLink("#快速开始"))
        assertTrue(isAnchorLink("#fn-abc"))
        // 外部链接与纯 `#` 不算
        assertEquals(false, isAnchorLink("https://example.com/#a"))
        assertEquals(false, isAnchorLink("#"))
        // `##` 开头的不是锚点（避免把 `##标题` 这种误判）
        assertEquals(false, isAnchorLink("##x"))
    }

    @Test
    fun `精确命中`() {
        val keys = mapOf("快速开始" to "b3", "安装" to "b7")
        assertEquals("b3", resolveAnchorKey("#快速开始", keys))
    }

    @Test
    fun `片段未 slug 化时再归一化一次`() {
        val keys = mapOf("quick-start" to "b2")
        assertEquals("b2", resolveAnchorKey("#Quick Start", keys))
    }

    @Test
    fun `脚注片段回退到脚注区`() {
        val keys = mapOf("footnotes" to "b9")
        assertEquals("b9", resolveAnchorKey("#fn-abc", keys))
        assertEquals("b9", resolveAnchorKey("#fn-1", keys))
    }

    @Test
    fun `找不到返回 null 而不是抛错`() {
        val keys = mapOf("其他" to "b1")
        assertNull(resolveAnchorKey("#不存在", keys))
        assertNull(resolveAnchorKey("#", keys))
    }
}
