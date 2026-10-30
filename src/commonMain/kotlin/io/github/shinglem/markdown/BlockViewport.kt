package io.github.shinglem.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import kotlin.math.roundToInt

/**
 * 大块的显示策略：限高 + 双向滚动 + 百分比缩放。
 *
 * 用于图表、原始 HTML、宽表格、长代码这类「一屏放不下」的块。
 */
public data class BlockViewportConfig(
    public val enabled: Boolean = true,
    /** 超过这个高度就出现纵向滚动条；`Dp.Unspecified` 表示不限高。 */
    public val maxHeight: Dp = 420.dp,
    /** 是否显示缩放控件。 */
    public val zoomable: Boolean = true,
    /** 固定档位，避免连续加减时的浮点累积（100% 变 78% 那种）。 */
    public val zoomLevels: List<Float> = listOf(
        0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f, 1.1f, 1.25f, 1.5f, 2.0f, 2.5f,
    ),
    /**
     * 生效的格式前缀。
     *
     * **不要放 `code`**：代码块自带横向滚动，再被本容器包一层就成了「滚动容器嵌套」，
     * Compose 会直接抛 `Horizontally scrollable component was measured with an infinity maximum width`。
     */
    public val formats: Set<String> = setOf("mermaid", "html", "table", "image.generic"),
) {
    internal fun matches(formats: Set<String>): Boolean = formats.any { f ->
        this.formats.any { prefix -> f == prefix || f.startsWith("$prefix:") }
    }
}

@Composable
internal fun BlockViewport(
    config: BlockViewportConfig,
    content: @Composable () -> Unit,
) {
    var zoom by remember { mutableStateOf(1f) }
    var contentHeight by remember { mutableStateOf(0) }
    var contentWidth by remember { mutableStateOf(0) }
    var boxWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    val maxHeightPx = with(density) {
        if (config.maxHeight == Dp.Unspecified) Int.MAX_VALUE else config.maxHeight.roundToPx()
    }
    val overflowVertical = contentHeight > maxHeightPx
    val overflowHorizontal = (contentWidth * zoom).roundToInt() > boxWidth && boxWidth > 0

    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()

    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { boxWidth = it.width },
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // 显式给内容一个**有限宽度**：横向滚动容器会给子项无限宽，
            // 而 mermaid 这类按宽度算高度的内容在无限宽下会直接不绘制。
            val availableWidth = maxWidth

            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = if (config.maxHeight == Dp.Unspecified) Dp.Unspecified else config.maxHeight)
                    .clipToBounds()
                    .verticalScroll(vScroll),
            ) {
                Row(Modifier.horizontalScroll(hScroll)) {
                    Box(
                        Modifier
                            .width(availableWidth)
                            .scaleLayout(zoom)
                            .onSizeChanged {
                                contentWidth = it.width
                                contentHeight = it.height
                            },
                    ) {
                        content()
                    }
                }
            }
        }

        if (config.zoomable && (overflowVertical || overflowHorizontal || zoom != 1f)) {
            val index = config.zoomLevels.indexOfFirst { it >= zoom - 0.001f }.let {
                if (it < 0) config.zoomLevels.lastIndex else it
            }
            ZoomBar(
                zoom = zoom,
                canZoomIn = index < config.zoomLevels.lastIndex,
                canZoomOut = index > 0,
                onZoomIn = { zoom = config.zoomLevels[(index + 1).coerceAtMost(config.zoomLevels.lastIndex)] },
                onZoomOut = { zoom = config.zoomLevels[(index - 1).coerceAtLeast(0)] },
                onReset = { zoom = 1f },
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            )
        }
    }
}

/**
 * 缩放后的**布局尺寸**跟着变。
 *
 * 只用 `graphicsLayer` 缩放不改变测量结果，滚动范围会对不上（内容画小了但滚动条仍按原尺寸算）。
 */
private fun Modifier.scaleLayout(scale: Float): Modifier = this
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(
            (placeable.width * scale).roundToInt().coerceAtLeast(0),
            (placeable.height * scale).roundToInt().coerceAtLeast(0),
        ) {
            placeable.place(0, 0)
        }
    }
    .graphicsLayer {
        scaleX = scale
        scaleY = scale
        transformOrigin = TransformOrigin(0f, 0f)
    }

@Composable
private fun ZoomBar(
    zoom: Float,
    canZoomIn: Boolean,
    canZoomOut: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ZoomButton("−", enabled = canZoomOut, onClick = onZoomOut)
            Text(
                text = "${(zoom * 100).roundToInt()}%",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .size(width = 40.dp, height = 20.dp)
                    .clickable(onClick = onReset)
                    .padding(top = 3.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            ZoomButton("＋", enabled = canZoomIn, onClick = onZoomIn)
        }
    }
}

@Composable
private fun ZoomButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = if (enabled) 0.9f else 0.4f),
                shape = RoundedCornerShape(4.dp),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}
