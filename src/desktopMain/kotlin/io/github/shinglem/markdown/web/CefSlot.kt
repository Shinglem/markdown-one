package io.github.shinglem.markdown.web

import androidx.compose.ui.graphics.ImageBitmap

/**
 * 一个块的浏览器槽位。
 *
 * 为什么需要它：CEF 初始化要 1–2 秒，而 Compose 的 `LaunchedEffect` 会因**尺寸变化**被取消重启
 * （长文档出现滚动条就会改变 `maxWidth`）。如果把「创建引擎 → 加载内容」写在这个 effect 里，
 * 取消会发生在 `withContext(Dispatchers.IO)` 返回之后，结果就是**引擎建好了但从没 load**，
 * 页面永远停在初始空白帧（实测：帧缓冲全零）。
 *
 * 所以这里把创建放到**独立线程**（不随协程取消），并把尺寸/内容请求缓存起来，
 * 等引擎就绪后一次性应用。
 */
internal class CefSlot(
    private val onFrame: (ImageBitmap) -> Unit,
    private val onError: (Throwable) -> Unit,
) {
    private val lock = Any()
    private var engine: CefEngine? = null
    private var creating = false
    private var disposed = false
    private var pendingWidth = 0
    private var pendingHeight = 0
    private var pendingHtml: String? = null

    /** 幂等：首次调用触发创建，之后只应用尺寸与内容。可安全地随 key 变化重复调用。 */
    fun request(width: Int, height: Int, html: String?) {
        val startCreate: Boolean
        val ready: CefEngine?
        synchronized(lock) {
            if (width > 0 && height > 0) {
                pendingWidth = width
                pendingHeight = height
            }
            if (html != null) pendingHtml = html
            ready = engine
            startCreate = ready == null && !creating && !disposed
            if (startCreate) creating = true
        }

        if (ready != null) {
            if (width > 0 && height > 0) ready.resize(width, height)
            html?.let(ready::load)
            return
        }
        if (!startCreate) return

        // 独立线程：不绑定 Compose 协程生命周期，因此不会因为重组被中途丢弃。
        Thread({
            runCatching { CefEngine(onFrame, onError) }
                .onSuccess { created ->
                    val (w, h, pending) = synchronized(lock) {
                        engine = created
                        creating = false
                        Triple(pendingWidth, pendingHeight, pendingHtml)
                    }
                    if (w > 0 && h > 0) created.resize(w, h)
                    pending?.let(created::load)
                    if (disposed) synchronized(lock) { engine = null }?.let { created.dispose() }
                }
                .onFailure { error ->
                    synchronized(lock) { creating = false }
                    onError(error)
                }
        }, "markdown-one-cef").apply { isDaemon = true }.start()
    }

    fun dispose() {
        val current = synchronized(lock) {
            disposed = true
            engine.also { engine = null }
        }
        current?.dispose()
    }
}
