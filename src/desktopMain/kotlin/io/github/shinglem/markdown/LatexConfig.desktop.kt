package io.github.shinglem.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.hrm.latex.renderer.model.LatexConfig
import com.hrm.latex.renderer.model.LatexTheme

/**
 * 行内与块级公式共用的排版配置。
 *
 * 两件事必须跟文档主题走，否则公式会和正文"两张皮"：
 * - 字号取当前 markdown 正文字号（标题里的公式因此也合适）；
 * - 颜色取 markdown 正文色（固定主题，不用 `LatexTheme.auto`，避免 `isDarkTheme`
 *   与宿主主题不一致时重新测量）。
 *
 * 参数显式传入而不是读 CompositionLocal：`MarkdownText` 在铺 `LocalMarkdownColors`
 * 之前就要算行内公式的 inline content。
 */
@Composable
internal fun rememberLatexConfig(color: Color, fontSize: TextUnit): LatexConfig =
    remember(color, fontSize) {
        LatexConfig(
            fontSize = if (fontSize != TextUnit.Unspecified) fontSize else 15.sp,
            theme = LatexTheme.light(color = color),
        )
    }
