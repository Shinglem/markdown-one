package io.github.shinglem.markdown

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 点击脚注序号要真的滚起来（不只是「链接存在」）。
 *
 * 真机反馈「脚注编号不可点击」。前一组测试只证明了 link annotation 存在，
 * 这一组把它接到真实滚动容器上，验证整条链路：命中 → `#fn-1` → 解析到脚注块 → bringIntoView。
 */
@OptIn(ExperimentalTestApi::class)
class FootnoteScrollTest {

    private fun document(): String = buildString {
        append("[^1]\n\n")
        repeat(60) { append("填充段落 $it，用来把脚注区推到视口之外。\n\n") }
        append("[^1]: 脚注定义在最后一屏。\n")
    }

    @Test
    fun `点击脚注序号滚动到脚注区`() = runComposeUiTest {
        val scroll = ScrollState(0)
        setContent {
            MaterialTheme {
                Box(Modifier.size(width = 320.dp, height = 200.dp)) {
                    Column(Modifier.verticalScroll(scroll)) {
                        MarkdownText(document())
                    }
                }
            }
        }
        waitForIdle()
        assertTrue(scroll.value == 0, "初始不该已经滚动：${scroll.value}")

        // 正文第一行只有一个上标字符（`[^1]`），点它左端即可命中链接
        onNodeWithText("1").performTouchInput { click(Offset(4f, centerY)) }
        waitForIdle()

        assertTrue(scroll.value > 0, "点击脚注序号后滚动位置仍为 0 —— 链接没有触发滚动")
    }
}
