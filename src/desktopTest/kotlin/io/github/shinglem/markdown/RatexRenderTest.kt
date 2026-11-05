package io.github.shinglem.markdown

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * RaTeX 接入的两条硬约束。
 *
 * 1. **占位尺寸必须和字号同量级**：`DisplayList` 的宽高是排版单位，忘乘字号会让行内公式
 *    小成一个点（或被裁掉），而这个错误在截图上很难一眼看出，用数值门槛钉住。
 * 2. **块级公式不能退化成源码**：画不出来时允许兜底显示 TeX，但能画的时候必须画出来。
 */
@OptIn(ExperimentalTestApi::class)
class RatexRenderTest {

    @Test
    fun `行内公式占位尺寸与字号同量级`() = runComposeUiTest {
        var contents: Map<String, InlineTextContent> = emptyMap()
        setContent {
            MaterialTheme {
                contents = mathInlineContents(setOf("x^2 + y^2"), Color.Black, 16.sp)
            }
        }
        waitForIdle()
        val placeholder = contents.values.single().placeholder
        // 16sp 的 `x² + y²` 宽高都应在几 sp 到几十 sp 之间（单位错一个量级就会掉出这个区间）
        assertTrue(placeholder.width.value in 4f..80f, "宽度异常：${placeholder.width}")
        assertTrue(placeholder.height.value in 6f..60f, "高度异常：${placeholder.height}")
    }

    @Test
    fun `画不出来的公式不进表`() = runComposeUiTest {
        var contents: Map<String, InlineTextContent> = emptyMap()
        setContent {
            MaterialTheme {
                // `\undefinedcommand` 不是 KaTeX 语法，应拿不到 DisplayList
                contents = mathInlineContents(setOf("\\undefinedcommand{x}"), Color.Black, 16.sp)
            }
        }
        waitForIdle()
        assertTrue(contents.isEmpty(), "非法语法不应生成 inline content，实际=$contents")
    }

    @Test
    fun `块级公式渲染成图形而不是源码`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MarkdownText("$$\nE = mc^2\n$$")
            }
        }
        waitForIdle()
        val text = renderedText()
        assertFalse(text.contains("$$"), "块级公式不应以源码出现，实际=$text")
        assertFalse(text.contains("E = mc^2"), "块级公式不应以纯文本出现，实际=$text")
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
