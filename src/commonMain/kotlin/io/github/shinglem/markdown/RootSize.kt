package io.github.shinglem.markdown

import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.IntSize

/**
 * 根节点尺寸 = 窗口可视区。
 *
 * 不用 `LocalWindowInfo.containerSize`（desktop 上可能一直是 0，会让可见性判定永远失效），
 * 也不用 `LocalView`（那是 Android API）。沿父链走到最外层即可。
 */
internal fun LayoutCoordinates.rootSize(): IntSize {
    var current: LayoutCoordinates? = this
    var last: LayoutCoordinates = this
    while (current != null) {
        last = current
        current = current.parentLayoutCoordinates
    }
    return last.size
}
