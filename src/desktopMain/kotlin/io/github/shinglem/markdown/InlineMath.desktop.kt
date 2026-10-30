package io.github.shinglem.markdown

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.sp
import io.github.shinglem.markdown.builtin.renderMathImage

/**
 * desktop 的行内公式：JLaTeXMath 出位图，占位按 `sp` 给出（与正文 15sp 同量级）。
 *
 * 渲染失败（语法不支持）时返回空表 —— annotator 里的 `appendInlineContent` 找不到 key，
 * Compose 会回退到 `alternateText`（TeX 源码），不会留空白。
 */
@Composable
internal actual fun mathInlineContents(texes: Set<String>): Map<String, InlineTextContent> {
    if (texes.isEmpty()) return emptyMap()
    val density = LocalDensity.current
    val color: Color = MaterialTheme.colorScheme.onSurface
    return remember(texes, density.density, color) {
        texes.mapNotNull { tex ->
            val image = renderMathImage(tex, display = false, scale = density.density, color = color)
                ?: return@mapNotNull null
            (MATH_KEY_PREFIX + tex) to InlineTextContent(
                placeholder = Placeholder(
                    width = image.widthDp.sp,
                    height = image.heightDp.sp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                ),
                children = {
                    Box {
                        Image(bitmap = image.bitmap, contentDescription = tex)
                    }
                },
            )
        }.toMap()
    }
}
