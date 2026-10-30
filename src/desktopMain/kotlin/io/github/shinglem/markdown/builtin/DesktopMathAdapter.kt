package io.github.shinglem.markdown.builtin

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.shinglem.markdown.MarkdownConfig
import io.github.shinglem.markdown.adapter.AdapterKind
import io.github.shinglem.markdown.adapter.BlockAdapter
import io.github.shinglem.markdown.adapter.FormatId
import io.github.shinglem.markdown.adapter.Formats
import io.github.shinglem.markdown.model.MdBlock
import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/**
 * 数学公式（`$...$` / `$$...$$`）：**纯 JVM 排版，不引 WebView / JCEF**。
 *
 * 排版交给 JLaTeXMath（Apache-2.0，Scilab 维护的 LaTeX 引擎），它把公式画到
 * AWT `Graphics2D`，这里转成 `ImageBitmap` 交给 Compose。
 *
 * 只在 desktop 注册（见 `PlatformAdapters.desktop.kt`）：其它 target 没有 AWT，
 * 会落到 `builtin.source` 显示 TeX 源码。
 *
 * 渲染失败（语法不支持）时**显示源码 + 提示**，不做静默替换。
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
        val density = LocalDensity.current
        val color = MaterialTheme.colorScheme.onSurface
        val image = remember(math.tex, math.display, density.density, color) {
            renderTex(math.tex, math.display, density.density, color)
        }
        if (image == null) {
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
        if (math.display) {
            Column(
                modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(bitmap = image, contentDescription = math.tex)
            }
        } else {
            Row(
                modifier = modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Image(bitmap = image, contentDescription = math.tex)
            }
        }
    }

    /**
     * TeX → 位图。
     *
     * @param scale 按屏幕密度放大，避免高分屏上发虚。
     * @param color 公式颜色跟随正文（JLaTeXMath 用前景色绘制字形）。
     */
    private fun renderTex(tex: String, display: Boolean, scale: Float, color: Color): ImageBitmap? =
        MathRenderCache.getOrPut("$tex|$display|$scale|${color.value}") {
            runCatching {
                val formula = TeXFormula(tex)
                val style = if (display) TeXConstants.STYLE_DISPLAY else TeXConstants.STYLE_TEXT
                // 16f 是正文可读的基准字号（与 markdown 正文 15sp 接近）。
                val icon = formula.createTeXIcon(style, 16f * scale)
                icon.setForeground(java.awt.Color(color.red, color.green, color.blue, color.alpha))
                // +2：给 0 基线字形（下划线、括号端点）留 1px 反锯齿余量，否则会被裁掉一点。
                val w = icon.iconWidth + 2
                val h = icon.iconHeight + 2
                if (w <= 0 || h <= 0) return@runCatching null
                val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
                val g = image.createGraphics()
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                icon.paintIcon(null, g, 1, 1)
                g.dispose()
                image.toComposeImageBitmap()
            }.getOrNull()
        }
}
