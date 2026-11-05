package io.github.shinglem.markdown

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import io.ratex.compose.RaTeX
import io.ratex.compose.rememberBlockingRaTeXDisplayList

/**
 * desktop 的行内公式：由 `ratex` 直接产出 [InlineTextContent]（Compose 原生矢量绘制）。
 *
 * 尺寸来自 `measure(displayList, emPx)`：`DisplayList` 的宽高是排版单位，乘上当前字号
 * 才是像素，所以占位框必须按字号换算，否则行内公式会被裁掉或撑行。
 *
 * 排不出来（语法不支持）时这张表里就没有该 key —— annotator 里的 `appendInlineContent`
 * 找不到 key，Compose 会回退到 `alternateText`（TeX 源码），不会留空白。
 */
@Composable
internal actual fun mathInlineContents(
    texes: Set<String>,
    color: Color,
    fontSize: TextUnit,
): Map<String, InlineTextContent> {
    if (texes.isEmpty()) return emptyMap()
    val density = LocalDensity.current
    val sp = if (fontSize.value > 0f) fontSize else 15.sp
    val emPx = sp.value * density.density
    return buildMap {
        texes.forEach { tex ->
            val displayList = rememberBlockingRaTeXDisplayList(tex, false, color).getOrNull() ?: return@forEach
            // `measure(displayList, emPx)` 是库的 internal API（外部用不了）；
            // 它的换算就是「排版单位 × 字号像素」，这里按同样规则算，多留 1px 抗锯齿余量。
            val widthSp = ((displayList.width * emPx).toFloat() / density.density + 1f).coerceAtLeast(1f).sp
            val heightSp = (((displayList.height + displayList.depth) * emPx).toFloat() / density.density + 1f)
                .coerceAtLeast(1f).sp
            put(
                MATH_KEY_PREFIX + tex,
                InlineTextContent(
                    Placeholder(
                        width = widthSp,
                        height = heightSp,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                    ),
                ) {
                    RaTeX(displayList, Modifier.fillMaxSize(), sp)
                },
            )
        }
    }
}
