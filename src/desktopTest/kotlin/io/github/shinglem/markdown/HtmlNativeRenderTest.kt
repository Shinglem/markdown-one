package io.github.shinglem.markdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import io.github.shinglem.markdown.adapter.LocalIsStreaming
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * HTML 块的原生渲染（`HtmlNativeAdapter`）。
 *
 * 验收口径来自 `.design/待修复问题.md` #9：每个样本渲染后**无空白块**、无源码泄漏、
 * 无异常；`<script>` 必须既不执行也不显示。
 */
@OptIn(ExperimentalTestApi::class)
class HtmlNativeRenderTest {

    @Test
    fun `容器与标题按结构渲染而不是源码`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MarkdownText(
                    """
                    <div align="center">
                    <h2>大标题</h2>
                    <p>正文段落</p>
                    </div>
                    """.trimIndent(),
                )
            }
        }
        waitForIdle()
        val text = renderedText()
        assertTrue(text.contains("大标题"), "标题内容应出现，实际=$text")
        assertTrue(text.contains("正文段落"), "段落内容应出现，实际=$text")
        assertFalse(text.contains("<div"), "容器标签不应作为源码出现，实际=$text")
        assertFalse(text.contains("<h2>"), "标题标签不应作为源码出现，实际=$text")
    }

    @Test
    fun `嵌套列表按层级渲染`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MarkdownText("<ul><li>甲<ul><li>甲一</li></ul></li><li>乙</li></ul>")
            }
        }
        waitForIdle()
        val text = renderedText()
        assertTrue(text.contains("甲一"), "嵌套项应出现，实际=$text")
        assertFalse(text.contains("<li>"), "列表标签不应作为源码出现，实际=$text")
    }

    @Test
    fun `script 既不执行也不显示`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MarkdownText("<div>前<script>alert('x')</script>后</div>")
            }
        }
        waitForIdle()
        val text = renderedText()
        assertTrue(text.contains("后"), "正文应出现，实际=$text")
        assertFalse(text.contains("alert"), "脚本内容不应出现，实际=$text")
    }

    @Test
    fun `病态输入回落源码而不是空白`() = runComposeUiTest {
        val deep = "<div>".repeat(40) + "深处文本" + "</div>".repeat(40)
        setContent {
            CompositionLocalProvider(LocalIsStreaming provides false) {
                MaterialTheme { MarkdownText(deep) }
            }
        }
        waitForIdle()
        val text = renderedText()
        assertTrue(text.contains("深处文本"), "超深结构应回落源码而不是丢内容，实际=$text")
    }

    @Test
    fun `pre 代码块保留换行`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MarkdownText("<pre><code>val x = 1\nval y = 2</code></pre>")
            }
        }
        waitForIdle()
        val text = renderedText()
        assertTrue(text.contains("val x = 1"), "代码内容应出现，实际=$text")
        assertTrue(text.contains("val y = 2"), "第二行也应出现，实际=$text")
    }

    @Test
    fun `html 表格按表格渲染`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MarkdownText("<table><tr><th>列一</th><th>列二</th></tr><tr><td>1</td><td>2</td></tr></table>")
            }
        }
        waitForIdle()
        val text = renderedText()
        assertTrue(text.contains("列一"), "表头应出现，实际=$text")
        assertTrue(text.contains("列二"), "表头应出现，实际=$text")
        assertFalse(text.contains("<td>"), "单元格标签不应作为源码出现，实际=$text")
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
