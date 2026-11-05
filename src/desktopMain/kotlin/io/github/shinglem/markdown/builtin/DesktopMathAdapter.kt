package io.github.shinglem.markdown.builtin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shinglem.markdown.MarkdownConfig
import io.github.shinglem.markdown.adapter.AdapterKind
import io.github.shinglem.markdown.adapter.BlockAdapter
import io.github.shinglem.markdown.adapter.FormatId
import io.github.shinglem.markdown.adapter.Formats
import io.github.shinglem.markdown.model.MdBlock
import io.github.shinglem.markdown.text.LocalMdColors
import io.github.shinglem.markdown.text.LocalMdTypography
import io.ratex.DisplayList
import io.ratex.compose.RaTeX
import io.ratex.compose.rememberBlockingRaTeXDisplayList

/**
 * 数学公式（`$$...$$` 块）：**纯 Compose 排版，不引 WebView**。
 *
 * 排版交给 `io.github.darriousliu:ratex`（MIT，Rust 核心 + Compose 矢量绘制），与行内公式
 * （`InlineMath.desktop.kt`）走同一条「先取 DisplayList 再绘制」的路径：取不到 DisplayList
 * 就说明这条公式排不出来，直接**显示源码 + 提示**，不做静默替换（绝不留空白）。
 *
 * 用阻塞版取 DisplayList（公式很小）：异步版会先渲染一帧"失败/空"，看起来像闪了一下源码。
 *
 * 只在 desktop 注册（见 `PlatformAdapters.desktop.kt`）：其它 target 落到
 * `builtin.source` 显示 TeX 源码。
 */
internal class DesktopMathAdapter(private val config: MarkdownConfig) : BlockAdapter {

    override val id: String = "builtin.math"
    override val kind: AdapterKind = AdapterKind.Native
    override val priority: Int = 100

    override fun formats(): Set<FormatId> = setOf(Formats.Math)

    override fun isAvailable(): Boolean = true

    override fun canRender(block: MdBlock): Boolean = block is MdBlock.Math

    @Composable
    override fun Render(block: MdBlock, modifier: Modifier) {
        val math = block as MdBlock.Math
        val color = LocalMdColors.current.text
        val fontSize = LocalMdTypography.current.text.fontSize.takeIf { it.value > 0f } ?: 15.sp
        // 内部已用 runCatching 包好：排不出来时 Result 是 failure，不会抛异常
        val displayList: DisplayList? = rememberBlockingRaTeXDisplayList(math.tex, math.display, color).getOrNull()

        if (displayList == null) {
            Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    text = config.mathFallbackText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = math.tex,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
            return
        }

        Box(
            modifier.fillMaxWidth().padding(vertical = 6.dp),
            contentAlignment = if (math.display) Alignment.Center else Alignment.CenterStart,
        ) {
            RaTeX(displayList, Modifier, fontSize)
        }
    }
}
