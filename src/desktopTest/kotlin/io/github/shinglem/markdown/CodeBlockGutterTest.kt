package io.github.shinglem.markdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 代码块的行号与行高亮。
 *
 * 行高亮是 `drawBehind` 画的，语义树里看不到颜色；这里只钉两件能自动化证明的事：
 * 行号真的渲染了、越界行号不会崩。配色靠 demo 截图核对。
 */
@OptIn(ExperimentalTestApi::class)
class CodeBlockGutterTest {

    private fun ComposeUiTest.texts(): List<String> {
        val out = mutableListOf<String>()
        fun walk(node: SemanticsNode) {
            node.config.getOrNull(SemanticsProperties.Text)?.forEach { out += it.text }
            node.children.forEach(::walk)
        }
        walk(onRoot().fetchSemanticsNode())
        return out
    }

    @Test
    fun `多行代码显示行号并保留语言标签`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MarkdownText("```kotlin\nval a = 1\nval b = 2\nval c = 3\n```")
            }
        }
        waitForIdle()
        val texts = texts()
        listOf("1", "2", "3").forEach { number ->
            assertTrue(number in texts, "应出现行号 $number，实际=$texts")
        }
        assertTrue(texts.any { it == "KOTLIN" }, "语言标签应保留，实际=$texts")
    }

    @Test
    fun `单行代码不显示行号`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MarkdownText("```kotlin\nval only = 1\n```")
            }
        }
        waitForIdle()
        val texts = texts()
        assertTrue("1" !in texts, "单行代码不该占一列行号，实际=$texts")
    }

    @Test
    fun `行高亮越界不会崩`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MarkdownText("```kotlin {2,99}\nval a = 1\nval b = 2\n```")
            }
        }
        waitForIdle()
        val texts = texts()
        assertTrue("1" in texts && "2" in texts, "行号应正常渲染，实际=$texts")
    }

    @Test
    fun `关掉行号后代码正文仍在`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MarkdownText(
                    markdown = "```kotlin {1}\nval a = 1\nval b = 2\n```",
                    config = MarkdownConfig(codeLineNumbers = false),
                )
            }
        }
        waitForIdle()
        val texts = texts()
        assertTrue("1" !in texts, "关掉行号后不该出现行号列，实际=$texts")
        assertTrue(texts.any { it.contains("val a") }, "代码正文应仍在，实际=$texts")
    }
}
