package io.github.shinglem.markdown

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import com.hrm.latex.renderer.measure.rememberLatexMeasurer

/**
 * desktop 的行内公式：由 `latex-renderer` 直接产出 [InlineTextContent]（Compose 原生矢量绘制）。
 *
 * 渲染失败（语法不支持）时返回空表 —— annotator 里的 `appendInlineContent` 找不到 key，
 * Compose 会回退到 `alternateText`（TeX 源码），不会留空白。
 */
@Composable
internal actual fun mathInlineContents(
    texes: Set<String>,
    color: Color,
    fontSize: TextUnit,
): Map<String, InlineTextContent> {
    if (texes.isEmpty()) return emptyMap()
    val config = rememberLatexConfig(color, fontSize)
    val measurer = rememberLatexMeasurer(config)
    return remember(texes, measurer, config) {
        texes.mapNotNull { tex ->
            measurer.inlineContent(tex, config)?.let { content -> (MATH_KEY_PREFIX + tex) to content }
        }.toMap()
    }
}

