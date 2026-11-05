package io.github.shinglem.markdown.text

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
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

/**
 * 图片点击：`(url, alt)`。
 *
 * 由 `MarkdownText` 自己接管并弹出全屏预览（见 `ImagePreview.kt`）；宿主不需要实现。
 * 行内图片与块级图片走同一个入口。
 */
internal val LocalMdOnImageClick = staticCompositionLocalOf<(String, String) -> Unit> { { _, _ -> } }

/** 代码块超过这个长度就跳过同步着色。 */
internal val LocalMdMaxSyncHighlightLength = staticCompositionLocalOf { 8_000 }

/**
 * 文内跳转：`#标题` / `#fn-xxx` 这类**片段链接**的目标。
 *
 * 库自己处理（滚动到对应块并高亮），宿主不必实现 —— 以前这类链接被当成普通外链
 * 交给宿主 `openExternalUrl`，于是点脚注序号、点 AI 生成的目录都毫无反应。
 */
internal val LocalMdAnchorScroll = staticCompositionLocalOf<(String) -> Unit> { {} }

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
    val onAnchorScroll = LocalMdAnchorScroll.current
    return remember(colors, typography, references, onLinkClick, onAnchorScroll) {
        MdInlineStyles(
            link = typography.textLink,
            code = typography.inlineCode.copy(background = colors.inlineCodeBackground).toSpanStyle(),
            highlight = SpanStyle(color = colors.highlightText, background = colors.highlightBackground),
            footnote = SpanStyle(baselineShift = BaselineShift.Superscript, fontWeight = FontWeight.SemiBold),
            references = references,
            linkListener = LinkInteractionListener { link ->
                val url = (link as? LinkAnnotation.Url)?.url ?: return@LinkInteractionListener
                if (isAnchorLink(url)) onAnchorScroll(url.removePrefix("#")) else onLinkClick(url)
            },
        )
    }
}

/** 是不是文内锚点（`#foo`）—— 这类链接交给自己滚动，不打开浏览器。 */
internal fun isAnchorLink(url: String): Boolean =
    url.startsWith("#") && url.length > 1 && !url.startsWith("##")

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
    // 只有 EOL / 标记 token 的片段会构建出空文本：不画，否则会多出一整行空白
    if (inline.text.isEmpty() && inline.images.isEmpty()) return
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
 * 尺寸优先级：`<img width/height>` 透传的显式尺寸 > 加载后的真实尺寸 > 默认占位（3em × 1.4em）。
 * 显式尺寸只指定一边时，另一边按真实宽高比推（未知比例时按默认比例）。
 * 无论哪种来源都遵守「不放大、不超过可用宽度」——与浏览器的 `max-width: 100%` 一致。
 */
private fun imageInlineContent(
    image: MdImageRef,
    sizes: MutableMap<String, IntSize>,
    capPx: Int,
    density: Density,
    fontSize: TextUnit,
): InlineTextContent {
    val loaded = sizes[image.url]
    val fontPx = if (fontSize.type == TextUnitType.Sp) with(density) { fontSize.toPx() } else with(density) { DEFAULT_FONT_SIZE.toPx() }
    val ratio = if (loaded != null && loaded.width > 0 && loaded.height > 0) {
        loaded.height.toFloat() / loaded.width.toFloat()
    } else {
        DEFAULT_IMAGE_RATIO
    }
    val explicitWidth = image.widthPx?.let { with(density) { it.dp.toPx() } }
    val explicitHeight = image.heightPx?.let { with(density) { it.dp.toPx() } }
    var width: Float
    var height: Float
    when {
        explicitWidth != null && explicitHeight != null -> {
            width = explicitWidth
            height = explicitHeight
        }

        explicitWidth != null -> {
            width = explicitWidth
            height = explicitWidth * ratio
        }

        explicitHeight != null -> {
            height = explicitHeight
            width = explicitHeight / ratio
        }

        loaded != null && loaded.width > 0 && loaded.height > 0 -> {
            width = loaded.width.toFloat()
            height = loaded.height.toFloat()
        }

        else -> {
            width = fontPx * 3f
            height = fontPx * 1.4f
        }
    }
    if (capPx > 0) {
        val cap = capPx.toFloat()
        val scale = min(1f, min(cap / width, cap / height))
        width *= scale
        height *= scale
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
        val detected = IntSize(result.width, result.height)
        // 只在真的变化时写：SnapshotStateMap 的 put 无条件记录写入，
        // 每帧重写同一个值会把自己拖进无限重组。
        if (detected.width > 0 && detected.height > 0 && sizes[image.url] != detected) {
            sizes[image.url] = detected
        }
    }
    val onImageClick = LocalMdOnImageClick.current
    Image(
        painter = painter,
        contentDescription = image.alt ?: alternateText,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize().clickable { onImageClick(image.url, image.alt.orEmpty()) },
    )
}

private val DEFAULT_FONT_SIZE = 14.sp

/** 未知宽高比时的默认比例（与 3em × 1.4em 的占位一致）。 */
private const val DEFAULT_IMAGE_RATIO = 1.4f / 3f

