package io.github.shinglem.markdown.builtin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.hrm.latex.renderer.Latex
import com.hrm.latex.renderer.measure.rememberLatexMeasurer
import io.github.shinglem.markdown.text.LocalMdColors
import io.github.shinglem.markdown.text.LocalMdTypography
import io.github.shinglem.markdown.MarkdownConfig
import io.github.shinglem.markdown.adapter.AdapterKind
import io.github.shinglem.markdown.adapter.BlockAdapter
import io.github.shinglem.markdown.adapter.FormatId
import io.github.shinglem.markdown.adapter.Formats
import io.github.shinglem.markdown.model.MdBlock
import io.github.shinglem.markdown.rememberLatexConfig

/**
 * 数学公式（`$$...$$` 块）：**纯 Compose 排版，不引 WebView**。
 *
 * 排版交给 `io.github.huarangmeng:latex-renderer`（MIT，KaTeX 字体 + 矢量绘制），
 * 与行内公式共用 [rememberLatexConfig]，字号/颜色跟随文档主题。
 *
 * 只在 desktop 注册（见 `PlatformAdapters.desktop.kt`）：其它 target 会落到
 * `builtin.source` 显示 TeX 源码。
 *
 * 先用 `measure()` 判一次能不能排版：失败时**显示源码 + 提示**，不做静默替换
 * （`Latex()` 自己遇到不支持的语法只会画空白）。
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
        val latexConfig = rememberLatexConfig(
            color = LocalMdColors.current.text,
            fontSize = LocalMdTypography.current.text.fontSize,
        )
        val measurer = rememberLatexMeasurer(latexConfig)
        val dimensions = remember(measurer, math.tex, latexConfig) {
            measurer.measure(math.tex, latexConfig)
        }

        if (dimensions == null) {
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
            Latex(latex = math.tex, config = latexConfig)
        }
    }
}

