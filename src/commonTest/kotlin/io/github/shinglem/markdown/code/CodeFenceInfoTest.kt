package io.github.shinglem.markdown.code

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 围栏标签里的行高亮范围：```` ```kotlin {1,3-5} ````。
 *
 * AI 常用来指「这几行」，所以解析必须宽容：写坏了只丢掉高亮，语言标签要保住。
 */
class CodeFenceInfoTest {

    @Test
    fun `只有语言时没有高亮`() {
        val info = parseCodeFence("kotlin")
        assertEquals("kotlin", info.language)
        assertTrue(info.highlightLines.isEmpty())
    }

    @Test
    fun `语言加单行高亮`() {
        val info = parseCodeFence("kotlin {3}")
        assertEquals("kotlin", info.language)
        assertEquals(setOf(3), info.highlightLines)
    }

    @Test
    fun `逗号与区间混用`() {
        val info = parseCodeFence("js {1,3-5,8}")
        assertEquals("js", info.language)
        assertEquals(setOf(1, 3, 4, 5, 8), info.highlightLines)
    }

    @Test
    fun `没有语言只有花括号时不产生语言`() {
        val info = parseCodeFence("{2-3}")
        assertNull(info.language)
        assertEquals(setOf(2, 3), info.highlightLines)
    }

    @Test
    fun `裸围栏返回 null 语言`() {
        val info = parseCodeFence(null)
        assertNull(info.language)
        assertTrue(info.highlightLines.isEmpty())
    }

    @Test
    fun `括号写坏时保住语言标签`() {
        // 少了右花括号：整串当语言（着色会失败但不丢内容），不当成高亮
        val broken = parseCodeFence("kotlin {1,2")
        assertEquals("kotlin {1,2", broken.language)
        assertTrue(broken.highlightLines.isEmpty())
    }

    @Test
    fun `非法行号被忽略`() {
        assertEquals(emptySet(), parseHighlightLines("a,b"))
        assertEquals(emptySet(), parseHighlightLines("0,-3"))
        assertEquals(emptySet(), parseHighlightLines("5-2"))
        assertEquals(emptySet(), parseHighlightLines(""))
    }

    @Test
    fun `超大区间被截断而不是撑爆集合`() {
        val lines = parseHighlightLines("1-999999999")
        assertTrue(lines.isNotEmpty())
        assertTrue(lines.size <= 100_000, "应截断到上限，实际 ${lines.size}")
        assertTrue(1 in lines)
    }
}
