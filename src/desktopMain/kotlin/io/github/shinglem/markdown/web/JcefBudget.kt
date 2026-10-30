package io.github.shinglem.markdown.web

import androidx.compose.runtime.mutableStateOf

/**
 * 浏览器名额：同一时刻最多 [capacity] 个块持有浏览器。
 *
 * 为什么只做名额、不做「引擎轮转复用」：把同一个 `CefBrowser` 交给不同块
 * （切帧回调 + 反复 `loadURL`）在 native 层会直接崩（实测 `exit 0x80000003`，无 Java 堆栈）。
 * 保留「一块一引擎」，靠**可见性驱动创建与释放**同样能把常驻进程数压到一两个。
 *
 * 名额**由可见性决定**，不是先到先得：块在 `onGloballyPositioned` 里上报自己到视口中心的距离，
 * 这里按「已持有 → 距离近」排序取前 `capacity` 个。否则离屏的块会占着名额不放，
 * 屏幕上真正可见的块反而一直显示「等待浏览器实例」。
 */
internal object JcefBudget {

    /** 可见块：key -> 到视口中心的距离（像素）。只含当前在范围内（含预取 margin）的块。 */
    private val distances = HashMap<String, Float>()

    private val ownersState = mutableStateOf<Set<String>>(emptySet())

    var capacity: Int = 2
        set(value) {
            val next = value.coerceAtLeast(1)
            if (field == next) return
            field = next
            recompute()
        }

    fun isGranted(key: String): Boolean = key in ownersState.value

    /**
     * 上报块的可见性。
     *
     * @param distance 到视口中心的距离（像素）；`null` = 不在可见范围内或已销毁。
     */
    fun report(key: String, distance: Float?) {
        val changed = if (distance == null) {
            distances.remove(key) != null
        } else {
            val previous = distances.put(key, distance)
            previous == null || previous != distance
        }
        if (changed) recompute()
    }

    /** 块离开组合：彻底移除，不再参与分配。 */
    fun forget(key: String) {
        if (distances.remove(key) == null) return
        recompute()
    }

    private fun recompute() {
        val current = ownersState.value
        val ordered = distances.entries
            .sortedWith(compareBy({ if (it.key in current) 0 else 1 }, { it.value }))
            .map { it.key }
        val next = ordered.take(capacity).toSet()
        if (next != current) ownersState.value = next
    }
}
