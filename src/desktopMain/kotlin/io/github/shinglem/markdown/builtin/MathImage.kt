package io.github.shinglem.markdown.builtin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/** 公式位图 + 逻辑尺寸（按密度还原后的 dp 值，供 inline content 计算占位）。 */
internal class MathImage(val bitmap: ImageBitmap, val widthDp: Float, val heightDp: Float)

/**
 * TeX → 位图。块级适配器与行内 content 共用同一条渲染与缓存路径。
 *
 * @param scale 按屏幕密度放大，避免高分屏发虚。
 * @param color 公式颜色跟随正文（JLaTeXMath 用前景色绘制字形）。
 */
internal fun renderMathImage(tex: String, display: Boolean, scale: Float, color: Color): MathImage? =
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
            MathImage(image.toComposeImageBitmap(), w / scale, h / scale)
        }.getOrNull()
    }
