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

    /** 「还需要浏览器吗」。释放后为 false；创建完成时若已变回 true 就保留引擎，避免白等一场。 */
    private var wanted = true
    private var pendingWidth = 0
    private var pendingHeight = 0
    private var pendingHtml: String? = null

    /** 已交给引擎的内容，避免同一份 HTML 重复加载。 */
    private var loadedHtml: String? = null

    /** 幂等：首次调用触发创建，之后只应用尺寸与内容。可安全地随 key 变化重复调用。 */
    fun request(width: Int, height: Int, html: String?) {
        val startCreate: Boolean
        val ready: CefEngine?
        synchronized(lock) {
            wanted = true
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
            if (html != null && html != loadedHtml) {
                loadedHtml = html
                ready.load(html)
            }
            return
        }
        if (!startCreate) return

        // 独立线程：不绑定 Compose 协程生命周期，因此不会因为重组被中途丢弃。
        Thread({
            // 首个浏览器上「创建后再 loadURL」会丢（实测首块永远全白），所以内容走初始 URL。
            val initialHtml = synchronized(lock) { pendingHtml }
            val initialUrl = initialHtml?.let(CefEngine::dataUrl) ?: "about:blank"
            runCatching { CefEngine(onFrame, onError, initialUrl) }
                .onSuccess { created ->
                    // 创建期间可能已经被释放/销毁：此时必须把刚建好的引擎丢掉，否则泄漏一个 Chromium 进程。
                    val ready = synchronized(lock) {
                        creating = false
                        if (disposed || !wanted) {
                            null
                        } else {
                            engine = created
                            loadedHtml = initialHtml
                            Triple(pendingWidth, pendingHeight, pendingHtml)
                        }
                    }
                    if (ready == null) {
                        created.dispose()
                    } else {
                        val (w, h, pending) = ready
                        if (w > 0 && h > 0) created.resize(w, h)
                        // 内容已经作为初始 URL 加载过了，只有创建期间内容更新才需要再 load。
                        if (pending != null && pending != initialHtml) {
                            synchronized(lock) { loadedHtml = pending }
                            created.load(pending)
                        }
                    }
                }
                .onFailure { error ->
                    synchronized(lock) { creating = false }
                    onError(error)
                }
        }, "markdown-one-cef").apply { isDaemon = true }.start()
    }

    /** 临时释放引擎（池位被别的块抢走），之后还能再次 [request] 重建。 */
    fun releaseEngine() {
        val current = synchronized(lock) {
            wanted = false
            val existing = engine
            engine = null
            existing
        }
        current?.dispose()
    }

    /** 永久销毁（块离开组合），之后 [request] 不再创建。 */
    fun dispose() {
        val current = synchronized(lock) {
            disposed = true
            val existing = engine
            engine = null
            existing
        }
        current?.dispose()
    }
}
