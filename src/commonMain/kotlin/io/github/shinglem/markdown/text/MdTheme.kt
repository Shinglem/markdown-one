package io.github.shinglem.markdown.text

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * 渲染配色。
 *
 * 这是**本库自己的**类型，不再借用第三方 renderer 的配色表 —— 那套类型只能配合它的
 * element composable 使用，留着它等于把渲染层永久绑在别人的控件上。
 */
@Immutable
public data class MdColors(
    public val text: Color,
    public val codeBackground: Color,
    public val inlineCodeBackground: Color,
    public val highlightText: Color,
    public val highlightBackground: Color,
    public val dividerColor: Color,
    public val tableBackground: Color,
)

/** 默认配色：跟随当前 Material 3 配色，嵌在宿主 App 里不需要额外配置。 */
@Composable
public fun mdColors(
    text: Color = MaterialTheme.colorScheme.onBackground,
    codeBackground: Color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f),
    inlineCodeBackground: Color = codeBackground,
    highlightText: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    highlightBackground: Color = MaterialTheme.colorScheme.secondaryContainer,
    dividerColor: Color = MaterialTheme.colorScheme.outlineVariant,
    tableBackground: Color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.02f),
): MdColors = MdColors(
    text = text,
    codeBackground = codeBackground,
    inlineCodeBackground = inlineCodeBackground,
    highlightText = highlightText,
    highlightBackground = highlightBackground,
    dividerColor = dividerColor,
    tableBackground = tableBackground,
)

/**
 * 字体表。
 *
 * 段落用 [paragraph]、列表标记用 [ordered] / [bullet]、列表正文用 [list]、
 * 表格与兜底文本用 [text]，与 Markdown 语义一一对应。
 */
@Immutable
public data class MdTypography(
    public val h1: TextStyle,
    public val h2: TextStyle,
    public val h3: TextStyle,
    public val h4: TextStyle,
    public val h5: TextStyle,
    public val h6: TextStyle,
    public val text: TextStyle,
    public val code: TextStyle,
    public val inlineCode: TextStyle,
    public val quote: TextStyle,
    public val paragraph: TextStyle,
    public val ordered: TextStyle,
    public val bullet: TextStyle,
    public val list: TextStyle,
    public val textLink: TextLinkStyles,
    public val table: TextStyle,
) {
    /** 标题样式按级别取；[level] 超出 1..6 时收敛到两端。 */
    public fun heading(level: Int): TextStyle = when (level.coerceIn(1, 6)) {
        1 -> h1
        2 -> h2
        3 -> h3
        4 -> h4
        5 -> h5
        else -> h6
    }
}

/**
 * 默认字体：与 Material 3 一致。
 *
 * `inlineCode` 的字号故意留成 `Unspecified` —— 行内码跟着上下文走
 * （标题里的行内码不该比标题本身更大），只把字体族换成等宽。
 */
@Composable
public fun mdTypography(
    h1: TextStyle = MaterialTheme.typography.displayLarge,
    h2: TextStyle = MaterialTheme.typography.displayMedium,
    h3: TextStyle = MaterialTheme.typography.displaySmall,
    h4: TextStyle = MaterialTheme.typography.headlineMedium,
    h5: TextStyle = MaterialTheme.typography.headlineSmall,
    h6: TextStyle = MaterialTheme.typography.titleLarge,
    text: TextStyle = MaterialTheme.typography.bodyLarge,
    code: TextStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
    inlineCode: TextStyle = text.copy(fontFamily = FontFamily.Monospace, fontSize = TextUnit.Unspecified),
    quote: TextStyle = MaterialTheme.typography.bodyMedium.plus(SpanStyle(fontStyle = FontStyle.Italic)),
    paragraph: TextStyle = MaterialTheme.typography.bodyLarge,
    ordered: TextStyle = MaterialTheme.typography.bodyLarge,
    bullet: TextStyle = MaterialTheme.typography.bodyLarge,
    list: TextStyle = MaterialTheme.typography.bodyLarge,
    textLink: TextLinkStyles = TextLinkStyles(
        style = MaterialTheme.typography.bodyLarge.copy(
            fontWeight = FontWeight.Bold,
            textDecoration = TextDecoration.Underline,
        ).toSpanStyle(),
    ),
    table: TextStyle = text,
): MdTypography = MdTypography(
    h1 = h1,
    h2 = h2,
    h3 = h3,
    h4 = h4,
    h5 = h5,
    h6 = h6,
    text = text,
    code = code,
    inlineCode = inlineCode,
    quote = quote,
    paragraph = paragraph,
    ordered = ordered,
    bullet = bullet,
    list = list,
    textLink = textLink,
    table = table,
)

/**
 * 尺寸表。
 *
 * 表格没有「固定列宽」：列宽按可用宽度均分（`weight`），超宽表格由
 * `BlockViewportConfig` 的缩放 + 横向滚动解决，避免小窗口里被截断。
 */
@Immutable
public data class MdDimens(
    public val dividerThickness: Dp,
    public val codeBackgroundCornerSize: Dp,
    public val blockQuoteThickness: Dp,
    public val tableCellPadding: Dp,
    public val tableCornerSize: Dp,
)

/** 默认尺寸。 */
@Composable
public fun mdDimens(
    dividerThickness: Dp = 1.dp,
    codeBackgroundCornerSize: Dp = 8.dp,
    blockQuoteThickness: Dp = 2.dp,
    tableCellPadding: Dp = 8.dp,
    tableCornerSize: Dp = 6.dp,
): MdDimens = MdDimens(
    dividerThickness = dividerThickness,
    codeBackgroundCornerSize = codeBackgroundCornerSize,
    blockQuoteThickness = blockQuoteThickness,
    tableCellPadding = tableCellPadding,
    tableCornerSize = tableCornerSize,
)

/** 间距表。 */
@Immutable
public data class MdPadding(
    /** 块与块之间的垂直间距。 */
    public val block: Dp,
    /** 整个列表的上下留白（每层各一份）。 */
    public val list: Dp,
    public val listItemTop: Dp,
    public val listItemBottom: Dp,
    /** 每嵌套一层的缩进。 */
    public val listIndent: Dp,
    public val codeBlock: PaddingValues,
    /** 引用的横向留白：竖条画在最左侧，正文从这里开始。 */
    public val blockQuote: PaddingValues,
    /** 引用正文的上下留白。 */
    public val blockQuoteText: PaddingValues,
)

/** 默认间距。 */
@Composable
public fun mdPadding(
    block: Dp = 2.dp,
    list: Dp = 4.dp,
    listItemTop: Dp = 4.dp,
    listItemBottom: Dp = 4.dp,
    listIndent: Dp = 12.dp,
    codeBlock: PaddingValues = PaddingValues(8.dp),
    blockQuote: PaddingValues = PaddingValues(horizontal = 16.dp),
    blockQuoteText: PaddingValues = PaddingValues(vertical = 4.dp),
): MdPadding = MdPadding(
    block = block,
    list = list,
    listItemTop = listItemTop,
    listItemBottom = listItemBottom,
    listIndent = listIndent,
    codeBlock = codeBlock,
    blockQuote = blockQuote,
    blockQuoteText = blockQuoteText,
)

/** 当前配色。由 [io.github.shinglem.markdown.MarkdownText] 统一提供。 */
public val LocalMdColors: ProvidableCompositionLocal<MdColors> = compositionLocalOf {
    error("MarkdownText 外层没有提供 MdColors")
}/** 当前字体表。 */
public val LocalMdTypography: ProvidableCompositionLocal<MdTypography> = compositionLocalOf {
    error("MarkdownText 外层没有提供 MdTypography")
}

/** 当前尺寸表。 */
public val LocalMdDimens: ProvidableCompositionLocal<MdDimens> = staticCompositionLocalOf {
    error("MarkdownText 外层没有提供 MdDimens")
}

/** 当前间距表。 */
public val LocalMdPadding: ProvidableCompositionLocal<MdPadding> = staticCompositionLocalOf {
    error("MarkdownText 外层没有提供 MdPadding")
}

/**
 * 只改水平对齐，其余字段原样保留。
 *
 * 用 `textAlign` 而不是布局约束来做居中：段落是整宽 `Text`，
 * 外层 `Box` 怎么摆放都不影响里面的文字与行内图片。
 */
public fun MdTypography.withTextAlign(align: TextAlign): MdTypography = copy(
    h1 = h1.copy(textAlign = align),
    h2 = h2.copy(textAlign = align),
    h3 = h3.copy(textAlign = align),
    h4 = h4.copy(textAlign = align),
    h5 = h5.copy(textAlign = align),
    h6 = h6.copy(textAlign = align),
    text = text.copy(textAlign = align),
    code = code.copy(textAlign = align),
    inlineCode = inlineCode.copy(textAlign = align),
    quote = quote.copy(textAlign = align),
    paragraph = paragraph.copy(textAlign = align),
    ordered = ordered.copy(textAlign = align),
    bullet = bullet.copy(textAlign = align),
    list = list.copy(textAlign = align),
    table = table.copy(textAlign = align),
)
