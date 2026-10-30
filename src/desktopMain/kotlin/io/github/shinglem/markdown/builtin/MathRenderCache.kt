package io.github.shinglem.markdown.builtin

import androidx.compose.ui.graphics.ImageBitmap

/**
 * 公式渲染结果缓存（desktop）。
 *
 * JLaTeXMath 的排版比文本贵得多（一条公式几毫秒到几十毫秒），而聊天里同一条消息
 * 会因为滚动、选中、hover 反复重组 —— 不缓存会把滚动拖卡。
 *
 * 键 = tex + 显示模式 + 缩放 + 颜色；用访问序 LRU，上限 64 条（足够一屏的公式量）。
 */
internal object MathRenderCache {

    private const val MAX_ENTRIES = 64

    private val map = object : LinkedHashMap<String, ImageBitmap?>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap?>?): Boolean =
            size > MAX_ENTRIES
    }

    /** 渲染都在 Compose 主线程，加锁只是兜底（宿主可能多窗口渲染）。 */
    private val lock = Any()

    fun getOrPut(key: String, compute: () -> ImageBitmap?): ImageBitmap? = synchronized(lock) {
        if (map.containsKey(key)) map[key] else compute().also { map[key] = it }
    }
}
