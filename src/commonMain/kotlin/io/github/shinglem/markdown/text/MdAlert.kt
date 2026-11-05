package io.github.shinglem.markdown.text

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.shinglem.markdown.model.AlertKind
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

/**
 * GitHub Alerts（`> [!NOTE]` …）。
 *
 * 解析层已经把 `ALERT` 节点映射成 [io.github.shinglem.markdown.model.MdBlock.Alert]，
 * 这里只负责画：左侧色条 + 类型图标 + 类型标题 + 正文。
 *
 * 正文交回 [MdFlow]：Alert 里的列表、代码、表格、嵌套引用各走自己的组件，
 * 与引用块的处理方式一致（`BLOCK_QUOTE` 标记 token 是节点内容的一部分，必须剔除）。
 *
 * 图标是**自绘矢量**而不是 `material-icons`：那套库在 markdown-one 里没有依赖，
 * 而 5 个 16dp 的小图形不值得为它引入整包（与 `DetailsAdapter` 的折叠箭头同一取舍）。
 */
@Composable
internal fun MdAlert(
    source: String,
    node: ASTNode,
    kind: AlertKind,
    modifier: Modifier = Modifier,
    depth: Int = 0,
) {
    val padding = LocalMdPadding.current
    val typography = LocalMdTypography.current
    val shape = RoundedCornerShape(8.dp)
    val accent = alertAccent(kind)
    val container = alertContainer(kind)
    val body = remember(node) {
        node.children.filterNot {
            it.type == MarkdownTokenTypes.BLOCK_QUOTE ||
                it.type == GFMTokenTypes.ALERT_TITLE ||
                it.type == MarkdownTokenTypes.EOL ||
                it.type == MarkdownTokenTypes.WHITE_SPACE
        }
    }
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = padding.block)
            .clip(shape)
            .background(container)
            // 竖条画在容器内侧（`drawBehind` 而不是 `fillMaxHeight()` 子元素）：
            // 后者在 Column 的无界高度约束下会退化成 Int.MAX_VALUE 而画不出来。
            .drawBehind {
                drawRect(
                    color = accent,
                    topLeft = Offset.Zero,
                    size = Size(3.dp.toPx(), size.height),
                )
            }
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AlertIcon(kind, accent, Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = alertLabel(kind),
                style = typography.text.copy(fontWeight = FontWeight.SemiBold),
                color = accent,
            )
        }
        if (body.isNotEmpty()) {
            MdFlow(source, body, typography.text, depth)
        }
    }
}

/** 类型标题文案；本库只做简体中文，与 `MarkdownConfig` 里其它兜底文案一致。 */
internal fun alertLabel(kind: AlertKind): String = when (kind) {
    AlertKind.Note -> "注意"
    AlertKind.Tip -> "提示"
    AlertKind.Important -> "重要"
    AlertKind.Warning -> "警告"
    AlertKind.Caution -> "谨慎"
}

/**
 * 类型强调色：色相按语义固定（蓝 / 绿 / 紫 / 琥珀 / 红），明度按当前主题明暗收敛。
 *
 * 为什么不直接用 `MaterialTheme.colorScheme` 的语义角色：M3 只有 primary / secondary /
 * tertiary / error 四组容器色，**装不下 5 种 Alert**，而宿主的自定义色板里
 * secondary 与 tertiary 往往同色（本仓库的 `AppPalette` 就是），会出现两种 Alert 长得一样。
 * 所以这里固定色相、跟着主题明暗调整明度，保证在任何背景下都读得清（对比度由
 * `MdAlertContrastTest` 断言）。
 */
@Composable
internal fun alertAccent(kind: AlertKind): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return remember(kind, dark) { alertAccentColor(kind, dark) }
}

/** 卡片底色：强调色的低透明度铺底，能叠在任何宿主背景上而不需要知道它是什么色。 */
@Composable
internal fun alertContainer(kind: AlertKind): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return remember(kind, dark) {
        alertAccentColor(kind, dark).copy(alpha = if (dark) ALERT_CONTAINER_ALPHA_DARK else ALERT_CONTAINER_ALPHA_LIGHT)
    }
}

/** 亮 / 暗主题下卡片底色的透明度（强调色叠在宿主背景上）。 */
internal const val ALERT_CONTAINER_ALPHA_LIGHT = 0.12f
internal const val ALERT_CONTAINER_ALPHA_DARK = 0.10f

/**
 * 明暗两侧各自的基准底色：只用于**挑选强调色**（保证强调色与自己铺出的底色对比度达标），
 * 不代表实际渲染背景 —— 实际底色是半透明的，会叠在宿主背景上。
 */
private val LIGHT_BASE = Color(0xFFFDFCF9)
private val DARK_BASE = Color(0xFF191614)

internal fun alertAccentColor(kind: AlertKind, dark: Boolean): Color {
    val hue = when (kind) {
        AlertKind.Note -> 210f
        AlertKind.Tip -> 145f
        AlertKind.Important -> 275f
        AlertKind.Warning -> 35f
        AlertKind.Caution -> 2f
    }
    val alpha = if (dark) ALERT_CONTAINER_ALPHA_DARK else ALERT_CONTAINER_ALPHA_LIGHT
    val base = if (dark) DARK_BASE else LIGHT_BASE
    fun ok(color: Color): Boolean =
        contrastRatio(color, color.copy(alpha = alpha).compositeOver(base)) >= MIN_ACCENT_CONTRAST

    return if (dark) {
        // 暗色底上要够亮：保持明度满格，对比不足时降低饱和度（亮度更高）
        var saturation = 0.55f
        while (saturation >= 0.15f) {
            val candidate = Color.hsv(hue, saturation, 1f)
            if (ok(candidate)) return candidate
            saturation -= 0.02f
        }
        Color.hsv(hue, 0.15f, 1f)
    } else {
        // 亮色底上要够暗：保持饱和度，逐步降明度
        var value = 0.72f
        while (value >= 0.15f) {
            val candidate = Color.hsv(hue, 0.85f, value)
            if (ok(candidate)) return candidate
            value -= 0.02f
        }
        Color.hsv(hue, 0.85f, 0.15f)
    }
}

/** WCAG 相对对比度（1..21）。 */
internal fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    val lighter = if (la > lb) la else lb
    val darker = if (la > lb) lb else la
    return (lighter + 0.05f) / (darker + 0.05f)
}

/** 强调色与其卡片的底色之间的最低对比度（正文级小字，WCAG AA 取 4.5）。 */
private const val MIN_ACCENT_CONTRAST = 4.5f

// ---------------------------------------------------------------------------
//  自绘图标
// ---------------------------------------------------------------------------

@Composable
private fun AlertIcon(kind: AlertKind, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = minOf(w, h) * 0.1f
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (kind) {
            AlertKind.Note -> {
                drawCircle(color, radius = minOf(w, h) / 2f - strokeWidth / 2f, style = stroke)
                drawCircle(color, radius = strokeWidth * 0.75f, center = Offset(w / 2f, h * 0.32f))
                drawLine(
                    color = color,
                    start = Offset(w / 2f, h * 0.46f),
                    end = Offset(w / 2f, h * 0.72f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }

            AlertKind.Tip -> {
                // 四角星：提示 = 灵感
                val cx = w / 2f
                val cy = h / 2f
                val outer = minOf(w, h) / 2f
                val inner = outer * 0.32f
                val path = Path().apply {
                    moveTo(cx, cy - outer)
                    quadraticTo(cx + inner * 0.25f, cy - inner * 0.25f, cx + outer, cy)
                    quadraticTo(cx + inner * 0.25f, cy + inner * 0.25f, cx, cy + outer)
                    quadraticTo(cx - inner * 0.25f, cy + inner * 0.25f, cx - outer, cy)
                    quadraticTo(cx - inner * 0.25f, cy - inner * 0.25f, cx, cy - outer)
                    close()
                }
                drawPath(path, color)
            }

            AlertKind.Important, AlertKind.Caution -> {
                drawCircle(color, radius = minOf(w, h) / 2f - strokeWidth / 2f, style = stroke)
                drawExclamation(color, w, h, strokeWidth, topFraction = 0.28f, bottomFraction = 0.56f)
            }

            AlertKind.Warning -> {
                val path = Path().apply {
                    moveTo(w / 2f, h * 0.06f)
                    lineTo(w * 0.97f, h * 0.9f)
                    lineTo(w * 0.03f, h * 0.9f)
                    close()
                }
                drawPath(path, color, style = stroke)
                drawExclamation(color, w, h, strokeWidth, topFraction = 0.4f, bottomFraction = 0.66f)
            }
        }
    }
}

/** 图标里的感叹号：一根竖线 + 一个点。 */
private fun DrawScope.drawExclamation(
    color: Color,
    w: Float,
    h: Float,
    strokeWidth: Float,
    topFraction: Float,
    bottomFraction: Float,
) {
    drawLine(
        color = color,
        start = Offset(w / 2f, h * topFraction),
        end = Offset(w / 2f, h * bottomFraction),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )
    drawCircle(
        color = color,
        radius = strokeWidth * 0.7f,
        center = Offset(w / 2f, h * (bottomFraction + 0.16f)),
    )
}
