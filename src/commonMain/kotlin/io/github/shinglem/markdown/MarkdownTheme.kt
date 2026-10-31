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
import io.github.shinglem.markdown.text.MdColors
import io.github.shinglem.markdown.text.MdDimens
import io.github.shinglem.markdown.text.MdPadding
import io.github.shinglem.markdown.text.MdTypography
import io.github.shinglem.markdown.text.mdColors
import io.github.shinglem.markdown.text.mdDimens
import io.github.shinglem.markdown.text.mdPadding
import io.github.shinglem.markdown.text.mdTypography

/**
 * 一套完整的渲染外观：颜色、字体、尺寸、内边距与页面背景。
 *
 * 不传（`MarkdownConfig.theme == null`）时全部沿用 Material 3 默认值。
 * 这些类型都是本库自己的（见 `io.github.shinglem.markdown.text`），渲染层不依赖任何
 * 第三方 renderer 的模型 —— 换实现时宿主代码不用跟着改。
 */
@Immutable
public data class MarkdownTheme(
    public val colors: MdColors,
    public val typography: MdTypography,
    public val dimens: MdDimens,
    public val padding: MdPadding,
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

    val colors = mdColors(
        text = palette.text,
        codeBackground = palette.surface,
        inlineCodeBackground = palette.inlineCodeBackground,
        dividerColor = palette.divider.copy(alpha = 0.32f),
        tableBackground = palette.surface,
        highlightText = palette.accentSoft,
        highlightBackground = palette.inlineCodeBackground,
    )

    val typography = mdTypography(
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
    )

    return MarkdownTheme(
        colors = colors,
        typography = typography,
        dimens = mdDimens(
            dividerThickness = 1.dp,
            codeBackgroundCornerSize = 8.dp,
            blockQuoteThickness = 3.dp,
            tableCornerSize = 6.dp,
        ),
        padding = mdPadding(),
        background = palette.background,
    )
}

/**
 * 跟随宿主 Material 3 配色的主题（**嵌在 App 里时用这个**）。
 *
 * 排版与 [outsidePaperTheme] 一致（标题比 M3 默认小一档、行高 1.6、块间距收窄），
 * 只是颜色全部取自当前 [MaterialTheme.colorScheme]，不画自己的背景 ——
 * 这样 Markdown 段落不会在一片自定义纸面上显得突兀。
 */
@Composable
public fun markdownMaterialTheme(): MarkdownTheme {
    val scheme = MaterialTheme.colorScheme
    val base = MaterialTheme.typography

    fun style(
        size: Int,
        weight: FontWeight = FontWeight.Normal,
        color: Color = scheme.onSurface,
        italic: Boolean = false,
    ): TextStyle = base.bodyMedium.copy(
        fontSize = size.sp,
        lineHeight = (size * 1.6f).sp,
        fontWeight = weight,
        color = color,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
    )

    val body = style(15)

    val colors = mdColors(
        text = scheme.onSurface,
        codeBackground = scheme.surfaceContainerHigh,
        inlineCodeBackground = scheme.secondaryContainer,
        dividerColor = scheme.outlineVariant,
        tableBackground = scheme.surfaceContainerLow,
        highlightText = scheme.onSecondaryContainer,
        highlightBackground = scheme.secondaryContainer,
    )

    val typography = mdTypography(
        h1 = style(24, FontWeight.Bold, scheme.primary),
        h2 = style(20, FontWeight.SemiBold),
        h3 = style(17, FontWeight.SemiBold),
        h4 = style(16, FontWeight.Medium, scheme.tertiary),
        h5 = style(15, FontWeight.Medium),
        h6 = style(14, FontWeight.Medium, scheme.onSurfaceVariant),
        text = body,
        paragraph = body,
        ordered = body,
        bullet = body,
        list = body,
        quote = style(15),
        code = style(13).copy(fontFamily = FontFamily.Monospace),
        inlineCode = style(14, color = scheme.onSecondaryContainer).copy(fontFamily = FontFamily.Monospace),
        table = body,
        textLink = TextLinkStyles(
            style = SpanStyle(color = scheme.primary, fontWeight = FontWeight.Medium),
        ),
    )

    return MarkdownTheme(
        colors = colors,
        typography = typography,
        dimens = mdDimens(
            dividerThickness = 1.dp,
            codeBackgroundCornerSize = 8.dp,
            blockQuoteThickness = 3.dp,
            tableCornerSize = 6.dp,
        ),
        padding = mdPadding(),
    )
}
