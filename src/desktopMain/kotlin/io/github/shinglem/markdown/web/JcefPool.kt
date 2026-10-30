package io.github.shinglem.markdown.web

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf

/**
 * 浏览器实例池。
 *
 * 每个 `CefBrowser` 是一个独立的 Chromium 渲染进程（实测单实例就有 5+ 个子进程），
 * 长文档里一块一个会很快把内存吃光。所以只保留固定数量的**活跃**浏览器。
 *
 * 分配策略是**先到先得 + 池满排队**，而不是抢占：抢占会让「3 个块可见、容量 2」的场景
 * 反复互相踢（乒乓），实测结果是三个块都在重建引擎、谁也渲染不出来。
 *
 * 排队中的块靠 [version] 感知池位释放：别的块滚出视口时版本号递增，触发它们重新申请。
 */
internal object JcefPool {

    /** 当前允许同时存活的最大浏览器数。 */
    @Volatile
    var capacity: Int = 2

    /** 最近使用的顺序，末尾最新。只在主线程（Compose）访问。 */
    private val order = ArrayDeque<String>()

    private val active = mutableStateMapOf<String, Boolean>()

    private val versionState = mutableStateOf(0)

    /** 池状态版本：任何占用变化都会 +1，排队中的块读它来重新申请。 */
    val version: Int get() = versionState.value

    fun isActive(key: String): Boolean = active[key] == true

    /**
     * 申请池位。
     *
     * @return true 表示拿到（或本来就有）；false 表示池满，调用方应显示占位并等 [version] 变化。
     */
    fun acquire(key: String): Boolean {
        if (active[key] == true) {
            touch(key)
            return true
        }
        if (active.size >= capacity.coerceAtLeast(1)) return false
        active[key] = true
        order.addLast(key)
        bump()
        return true
    }

    /** 标记最近使用；只有活跃块才参与 LRU。 */
    fun touch(key: String) {
        if (active[key] != true) return
        if (order.lastOrNull() == key) return
        order.remove(key)
        order.addLast(key)
    }

    fun release(key: String) {
        if (active.remove(key) == null) return
        order.remove(key)
        bump()
    }

    private fun bump() {
        versionState.value += 1
    }
}
