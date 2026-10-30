package io.github.shinglem.markdown

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * desktop：原生滚动条，常驻可见。
 *
 * 厚度写死（10dp），**长度由调用方通过 modifier 给出实际尺寸** ——
 * 这里不能写 `fillMaxHeight()` / `fillMaxWidth()`：在无界约束下会退化成 `Int.MAX_VALUE`，
 * Skia 会直接拒绝（`Size(10 x 2147483647) is out of range`）。
 */
@Composable
internal actual fun BlockScrollbarOverlay(
    vScroll: ScrollState,
    hScroll: ScrollState,
    vertical: Boolean,
    horizontal: Boolean,
    modifier: Modifier,
) {
    when {
        vertical -> VerticalScrollbar(
            adapter = rememberScrollbarAdapter(vScroll),
            modifier = modifier.width(10.dp),
        )

        horizontal -> HorizontalScrollbar(
            adapter = rememberScrollbarAdapter(hScroll),
            modifier = modifier.height(10.dp),
        )
    }
}
