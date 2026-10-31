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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 嵌套在引用 / 列表里的块，必须和顶层一样走适配器链。
 *
 * 这一层曾经是写死的 `when`：引用里的 `$$…$$`、列表里的 mermaid、嵌套表格都被当成
 * 源码吐出来。现在 `MdRoutedBlock` 用 `MdBlockFactory` + 同一个 `AdapterRegistry` 分发，
 * 这些断言防止它退回到「只处理 8 种类型」的老路。
 */
@OptIn(ExperimentalTestApi::class)
class NestedBlockRoutingTest {

    @Test
    fun `引用里的块级公式按公式渲染而不是源码`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MarkdownText(
                    """
                    > $$
                    > E = mc^2
                    > $$
                    """.trimIndent(),
                )
            }
        }
        waitForIdle()
        val text = renderedText()
        assertFalse(text.contains("$$"), "块级公式不应以源码出现，实际=$text")
    }

    @Test
    fun `引用里的表格按表格渲染而不是源码`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MarkdownText(
                    """
                    > | 列 A | 列 B |
                    > | --- | --- |
                    > | 1 | 2 |
                    """.trimIndent(),
                )
            }
        }
        waitForIdle()
        val text = renderedText()
        assertTrue(text.contains("列 A"), "表格单元格内容应出现，实际=$text")
        assertFalse(text.contains("| ---"), "表格分隔行不应作为源码出现，实际=$text")
    }

    @Test
    fun `列表项里的代码块仍然渲染成代码块`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MarkdownText(
                    """
                    - 一项

                      ```kotlin
                      val x = 1
                      ```
                    """.trimIndent(),
                )
            }
        }
        waitForIdle()
        val text = renderedText()
        assertTrue(text.contains("val x = 1"), "代码内容应出现，实际=$text")
        assertFalse(text.contains("```"), "围栏不应作为源码出现，实际=$text")
    }

    private fun ComposeUiTest.renderedText(): String {
        val out = mutableListOf<String>()
        fun walk(node: SemanticsNode) {
            node.config.getOrNull(SemanticsProperties.Text)?.forEach { out += it.text }
            node.children.forEach(::walk)
        }
        walk(onRoot().fetchSemanticsNode())
        return out.joinToString("\n")
    }
}
