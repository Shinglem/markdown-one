package io.github.shinglem.markdown.text

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import org.intellij.markdown.ast.ASTNode
import kotlin.math.min

/** 整篇文档的行内公式 inline content（tex → 内容）。由 `MarkdownText` 预先算好。 */
internal val LocalMdMathContent = staticCompositionLocalOf<Map<String, InlineTextContent>> { emptyMap() }

/** 整篇文档的链接定义（label → url），引用式链接靠它解析。 */
internal val LocalMdReferences = staticCompositionLocalOf<Map<String, String>> { emptyMap() }

/** 链接点击：库自身**不打开任何东西**，只把地址交给宿主（见 SECURITY.md）。 */
internal val LocalMdOnLinkClick = staticCompositionLocalOf<(String) -> Unit> { {} }

/** 代码块超过这个长度就跳过同步着色。 */
internal val LocalMdMaxSyncHighlightLength = staticCompositionLocalOf { 8_000 }

/**
 * 从当前主题拼出行内构建需要的样式。
 *
 * 在这里（而不是构建函数内部）读 CompositionLocal，是为了让构建函数保持纯函数、
 * 可以被 `remember` 缓存。
 */
@Composable
internal fun rememberMdInlineStyles(): MdInlineStyles {
    val colors = LocalMdColors.current
    val typography = LocalMdTypography.current
    val references = LocalMdReferences.current
    val onLinkClick = LocalMdOnLinkClick.current
    return remember(colors, typography, references, onLinkClick) {
        MdInlineStyles(
            link = typography.textLink,
            code = typography.inlineCode.copy(background = colors.inlineCodeBackground).toSpanStyle(),
            highlight = SpanStyle(color = colors.highlightText, background = colors.highlightBackground),
            references = references,
            linkListener = LinkInteractionListener { link ->
                (link as? LinkAnnotation.Url)?.url?.let(onLinkClick)
            },
        )
    }
}

/** 一段行内文本（纯文本节点）。 */
@Composable
internal fun MdInlineText(
    source: String,
    node: ASTNode,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) = MdInlineText(source, node.children, style, modifier, maxLines, overflow)

/**
 * 一段行内文本（一组兄弟节点）。
 *
 * `maxLines` / `overflow` 给表格单元格用；正文一律不截断。
 */
@Composable
internal fun MdInlineText(
    source: String,
    children: List<ASTNode>,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val inlineStyles = rememberMdInlineStyles()
    val inline = remember(source, children, inlineStyles) { buildMdInline(source, children, inlineStyles) }
    val density = LocalDensity.current
    val defaultColor = LocalMdColors.current.text

    // 行内图片的占位尺寸取决于可用宽度：第一次布局后才有真实宽度，随后收敛。
    var widthPx by remember { mutableStateOf(0) }
    val imageSizes = remember(inline) { mutableStateMapOf<String, IntSize>() }
    val inlineContent = rememberMdInlineContent(inline.images, imageSizes.toMap(), widthPx, density, style, imageSizes)

    BasicText(
        text = inline.text,
        modifier = modifier.onSizeChanged { if (it.width > 0) widthPx = it.width },
        style = style,
        inlineContent = inlineContent,
        maxLines = maxLines,
        overflow = overflow,
        color = { if (style.color.isSpecified) style.color else defaultColor },
    )
}

/** 公式内容来自文档级表格，图片内容按本段实际用到的地址现算。 */
@Composable
private fun rememberMdInlineContent(
    images: List<MdImageRef>,
    sizes: Map<String, IntSize>,
    capPx: Int,
    density: Density,
    style: TextStyle,
    sizeSink: MutableMap<String, IntSize>,
): Map<String, InlineTextContent> {
    val math = LocalMdMathContent.current
    if (images.isEmpty()) return math
    val imageContent = remember(images, sizes, capPx, density, style.fontSize) {
        images.associate { image ->
            image.key to imageInlineContent(image, sizeSink, capPx, density, style.fontSize)
        }
    }
    return math + imageContent
}

/**
 * 行内图片的占位框。
 *
 * 尺寸未知时先按 3em × 1.4em 占位（加载完成后按真实宽高比收敛）；
 * 已知尺寸时按「不放大、不超过可用宽度」缩放 —— 小图标保持原尺寸，
 * 大图缩到容器宽度，和浏览器的 `max-width: 100%` 行为一致。
 */
private fun imageInlineContent(
    image: MdImageRef,
    sizes: MutableMap<String, IntSize>,
    capPx: Int,
    density: Density,
    fontSize: TextUnit,
): InlineTextContent {
    val size = sizes[image.url]
    val fontPx = if (fontSize.type == TextUnitType.Sp) with(density) { fontSize.toPx() } else with(density) { DEFAULT_FONT_SIZE.toPx() }
    val width: Float
    val height: Float
    if (size != null && size.width > 0 && size.height > 0) {
        val cap = if (capPx > 0) capPx.toFloat() else size.width.toFloat()
        val scale = min(1f, min(cap / size.width, cap / size.height))
        width = size.width * scale
        height = size.height * scale
    } else {
        width = fontPx * 3f
        height = fontPx * 1.4f
    }
    return InlineTextContent(
        Placeholder(
            width = with(density) { width.toSp() },
            height = with(density) { height.toSp() },
            placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
        ),
    ) { alternateText ->
        MdInlineImage(image, sizes, alternateText)
    }
}

/**
 * 行内图片本体。
 *
 * 占位框先于图片确定尺寸，所以真实宽高只能从 [AsyncImagePainter] 的加载结果里取；
 * 拿到之后写回状态表，下一次组合按真实宽高比重排。
 */
@Composable
private fun MdInlineImage(image: MdImageRef, sizes: MutableMap<String, IntSize>, alternateText: String) {
    val painter = rememberAsyncImagePainter(model = image.url)
    val state by painter.state.collectAsState()
    SideEffect {
        val success = state as? AsyncImagePainter.State.Success ?: return@SideEffect
        val result = success.result.image
        if (result.width > 0 && result.height > 0) {
            sizes[image.url] = IntSize(result.width, result.height)
        }
    }
    Image(
        painter = painter,
        contentDescription = image.alt ?: alternateText,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize(),
    )
}

private val DEFAULT_FONT_SIZE = 14.sp

