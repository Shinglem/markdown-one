package io.github.shinglem.markdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownDimens
import com.mikepenz.markdown.model.MarkdownPadding
import com.mikepenz.markdown.model.MarkdownTypography
import com.mikepenz.markdown.model.markdownDimens
import com.mikepenz.markdown.model.markdownPadding

/**
 * 一套完整的渲染外观：颜色、字体、尺寸、内边距与页面背景。
 *
 * 不传（`MarkdownConfig.theme == null`）时全部沿用 Material 3 默认值。
 */
@Immutable
public data class MarkdownTheme(
    public val colors: MarkdownColors,
    public val typography: MarkdownTypography,
    public val dimens: MarkdownDimens,
    public val padding: MarkdownPadding,
    /** 页面背景；`Color.Unspecified` 表示不画（透出父容器）。 */
    public val background: Color = Color.Unspecified,
)

/**
 * 配色。取自 typora-outside-theme 的 `outside-paper.css`。
 */
@Immutable
public data class PaperPalette(
    public val background: Color,
    public val surface: Color,
    public val text: Color,
    public val textMuted: Color,
    public val accent: Color,
    public val accentSoft: Color,
    public val accentWarm: Color,
    public val inlineCodeBackground: Color,
    public val divider: Color,
    public val quoteBackground: Color,
)

/** 暖白纸面 + 砖红强调。 */
public val OutsidePaper: PaperPalette = PaperPalette(
    background = Color(0xFFFDFCF9),
    surface = Color(0xFFF6F0E4),
    text = Color(0xFF5A524A),
    textMuted = Color(0xFF9A9080),
    accent = Color(0xFFA8433A),
    accentSoft = Color(0xFFC2503C),
    accentWarm = Color(0xFFB05A2E),
    inlineCodeBackground = Color(0xFFF7E7DF),
    divider = Color(0xFFC2A493),
    quoteBackground = Color(0xFFC8503C),
)

/** 保留暖调的暗色纸面。 */
public val OutsidePaperDark: PaperPalette = PaperPalette(
    background = Color(0xFF1E1B18),
    surface = Color(0xFF2A2521),
    text = Color(0xFFD7CFC5),
    textMuted = Color(0xFF8D8378),
    accent = Color(0xFFE0755F),
    accentSoft = Color(0xFFE38C6B),
    accentWarm = Color(0xFFD98F5C),
    inlineCodeBackground = Color(0xFF35292A),
    divider = Color(0xFF5A4A40),
    quoteBackground = Color(0xFFE0755F),
)

/**
 * Outside Paper 风格：暖白纸面、砖红强调、米黄代码块。
 *
 * 标题字号比 Material 默认小一档 —— `displayLarge` 在桌面窗口里会把 h1 撑到近 40sp，
 * 整篇看起来像海报而不是文档。行高统一 1.6，与 paper.css 一致。
 */
@Composable
public fun outsidePaperTheme(dark: Boolean = false): MarkdownTheme {
    val palette = if (dark) OutsidePaperDark else OutsidePaper
    val base = MaterialTheme.typography

    fun style(
        size: Int,
        weight: FontWeight = FontWeight.Normal,
        color: Color = palette.text,
        italic: Boolean = false,
    ): TextStyle = base.bodyMedium.copy(
        fontSize = size.sp,
        lineHeight = (size * 1.6f).sp,
        fontWeight = weight,
        color = color,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
    )

    val body = style(15)

    val colors = markdownColor(
        text = palette.text,
        codeBackground = palette.surface,
        inlineCodeBackground = palette.inlineCodeBackground,
        dividerColor = palette.divider.copy(alpha = 0.32f),
        tableBackground = palette.surface,
        darkTheme = dark,
        highlightText = palette.accentSoft,
        highlightBackground = palette.inlineCodeBackground,
    )

    val typography = markdownTypography(
        h1 = style(24, FontWeight.Bold, palette.accent),
        h2 = style(20, FontWeight.SemiBold),
        h3 = style(17, FontWeight.SemiBold),
        h4 = style(16, FontWeight.Medium, palette.accentWarm),
        h5 = style(15, FontWeight.Medium),
        h6 = style(14, FontWeight.Medium, palette.textMuted),
        text = body,
        paragraph = body,
        ordered = body,
        bullet = body,
        list = body,
        quote = style(15),
        code = style(13).copy(fontFamily = FontFamily.Monospace),
        inlineCode = style(14, color = palette.accentSoft).copy(fontFamily = FontFamily.Monospace),
        table = body,
        textLink = TextLinkStyles(
            style = SpanStyle(color = palette.accent, fontWeight = FontWeight.Medium),
        ),
        alertTitle = body.copy(fontWeight = FontWeight.SemiBold),
    )

    return MarkdownTheme(
        colors = colors,
        typography = typography,
        dimens = markdownDimens(
            dividerThickness = 1.dp,
            codeBackgroundCornerSize = 8.dp,
            blockQuoteThickness = 3.dp,
            tableCornerSize = 6.dp,
        ),
        padding = markdownPadding(),
        background = palette.background,
    )
}
