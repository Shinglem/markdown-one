package io.github.shinglem.markdown

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ratex.compose.rememberBlockingRaTeXDisplayList
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

    @Test
    fun `超宽块级公式可以横向滚动`() = runComposeUiTest {
        // 20 项求和在 16sp 下约 730sp 宽，放进 240dp 的容器必然溢出
        val wide = "a_1+a_2+a_3+a_4+a_5+a_6+a_7+a_8+a_9+a_{10}+a_{11}+a_{12}" +
            "+a_{13}+a_{14}+a_{15}+a_{16}+a_{17}+a_{18}+a_{19}+a_{20}"
        setContent {
            MaterialTheme {
                Box(Modifier.width(240.dp)) {
                    MarkdownText("$$\n$wide\n$$")
                }
            }
        }
        waitForIdle()
        val range = horizontalScrollRange()
        assertTrue(range != null, "超宽公式应包一层横向滚动")
        assertTrue(range.maxValue() > 0f, "滚动范围应大于 0，实际=$range")
    }

    @Test
    fun `短公式不产生滚动范围`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(Modifier.width(400.dp)) {
                    MarkdownText("$$\nE = mc^2\n$$")
                }
            }
        }
        waitForIdle()
        val range = horizontalScrollRange()
        // 短公式仍可能有滚动容器（实现统一），但范围必须是 0；有范围就说明误判了宽度
        assertTrue(range == null || range.maxValue() == 0f, "短公式不该出现可滚动范围，实际=$range")
    }

    /**
     * 真实会话里失败的形状：多行 `aligned`，行尾带可选行距参数 `\\[2pt]`，
     * 行内还有 `\left(` / `\right)` 与 `\quad`。
     */
    @Test
    fun `多行 aligned 公式带行距参数也能渲染`() = runComposeUiTest {
        val tex = """
            \begin{aligned}
            \sum_{i=1}^{20}\frac{1}{i(i+1)}
            &= \frac{1}{1\cdot 2}+\frac{1}{2\cdot 3}+\frac{1}{3\cdot 4}+\cdots+\frac{1}{20\cdot 21}\\[2pt]
            &= \left(\frac{1}{1}-\frac{1}{2}\right)
             + \left(\frac{1}{2}-\frac{1}{3}\right)
             + \cdots
             + \left(\frac{1}{20}-\frac{1}{21}\right)\\[2pt]
            &= 1-\frac{1}{21}
             = \frac{20}{21}
             \approx 0.952381
            \end{aligned}
        """.trimIndent()
        setContent {
            MaterialTheme {
                MarkdownText("$$\n$tex\n$$")
            }
        }
        waitForIdle()
        val text = renderedText()
        assertFalse(text.contains("begin{aligned}"), "多行公式退化成源码，实际=$text")
    }

    private fun ComposeUiTest.horizontalScrollRange(): ScrollAxisRange? {
        var found: ScrollAxisRange? = null
        fun walk(node: SemanticsNode) {
            node.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)?.let { found = it }
            node.children.forEach(::walk)
        }
        walk(onRoot().fetchSemanticsNode())
        return found
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
