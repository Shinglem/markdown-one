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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 行内公式在**真实文本管线**里的输出。
 *
 * 只验证两件事，都对应一次真实踩坑：
 * - 紧贴标点的 `$…$` 不渲染公式，但**美元符必须保留**（mikepenz 的 else 分支会静默丢掉
 *   未识别的 token，不接管就是 `时，x。`）；
 * - 有前导空格的 `$…$` 渲染成 inline content，原文里的 `$` 不再出现在文本里。
 */
@OptIn(ExperimentalTestApi::class)
class InlineMathRenderTest {

    @Test
    fun `紧贴标点的公式保留源码`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MarkdownText("时，\$x\$。")
            }
        }
        assertEquals("时，\$x\$。", renderedText())
    }

    @Test
    fun `前导空格的公式被替换成行内内容`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MarkdownText("设 \$x\$ 为实数。")
            }
        }
        val text = renderedText()
        assertTrue(!text.contains('$'), "公式应被 inline content 取代，实际=$text")
    }

    private fun ComposeUiTest.renderedText(): String {
        val out = mutableListOf<String>()
        fun walk(node: SemanticsNode) {
            node.config.getOrNull(SemanticsProperties.Text)?.forEach { out += it.text }
            node.children.forEach(::walk)
        }
        walk(onRoot().fetchSemanticsNode())
        return out.joinToString("")
    }
}
