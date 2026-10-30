package io.github.shinglem.markdown.web

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.awt.Rectangle
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.util.Base64

/**
 * JCEF 的离屏（OSR）封装：**全部走反射**，库对 `org.cef.*` 没有编译期依赖。
 *
 * 为什么必须 OSR：CEF 的常规浏览器是 heavyweight AWT Canvas，塞进 Compose 的 `SwingPanel`
 * 会因 z-order 问题什么都画不出来（实测）。OSR 直接给 BGRA 帧，我们自己画到 Compose Canvas 上。
 *
 * `onPaint` 在 CEF 线程回调，帧通过 [onFrame] 交给调用方切回 Compose。
 */
internal class CefEngine(
    private val onFrame: (ImageBitmap) -> Unit,
    private val onError: (Throwable) -> Unit = {},
    initialUrl: String = "about:blank",
) {
    @Volatile
    private var viewWidth: Int = 640

    @Volatile
    private var viewHeight: Int = 320

    private val client: Any
    private val browser: Any

    init {
        val app = CefBootstrap.app()

        client = CefRefs.createClient.invoke(app)
            ?: error("CefApp.createClient 返回 null")

        val requestContext = CefRefs.getGlobalContext.invoke(null)
            ?: error("CefRequestContext.getGlobalContext 返回 null")

        // 内容直接用初始 URL 传进去：创建后再 `loadURL` 在**首个浏览器**上会丢（实测首块永远全白）。
        browser = CefRefs.osrCtor.newInstance(client, initialUrl, requestContext, newRenderHandler())
            ?: error("CefBrowserOsrWithHandler 构造返回 null")

        // OSR 浏览器惰性创建：不显式创建就没有渲染进程，onPaint 永远不会来（实测）。
        runCatching { CefRefs.createImmediately.invoke(browser) }
        CefRefs.wasResized.invoke(browser, viewWidth, viewHeight)
    }

    fun resize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (width == viewWidth && height == viewHeight) return
        viewWidth = width
        viewHeight = height
        runCatching { CefRefs.wasResized.invoke(browser, width, height) }
    }

    fun load(html: String) {
        runCatching { CefRefs.loadURL.invoke(browser, dataUrl(html)) }
            .onFailure { onError(it) }
    }

    companion object {
        fun dataUrl(html: String): String {
            val encoded = Base64.getEncoder().encodeToString(html.toByteArray(Charsets.UTF_8))
            return "data:text/html;base64,$encoded"
        }
    }

    /** 关闭本引擎的浏览器与 client；`CefApp` 是全局单例，不能在这里关。 */
    fun dispose() {
        // `close` / `dispose` 内部会等 CEF 的 UI 线程，直接在主线程调会把窗口卡死（实测：窗口不出）。
        Thread({
            runCatching { CefRefs.close.invoke(browser, true) }
            runCatching { CefRefs.dispose.invoke(client) }
        }, "markdown-one-cef-dispose").apply { isDaemon = true }.start()
    }

    /** `CefRenderHandler` 的动态实现：只关心 `getViewRect` / `getScreenInfo` / `onPaint`。 */
    private fun newRenderHandler(): Any {
        val handler = java.lang.reflect.InvocationHandler { _, method, args ->
            when (method.name) {
                "getViewRect" -> Rectangle(0, 0, viewWidth, viewHeight)

                // 返回 false 或留空字段，CEF 会认为没有可用的屏幕信息而**不出帧**。
                "getScreenInfo" -> {
                    val info = args?.getOrNull(1)
                    if (info == null) {
                        false
                    } else {
                        runCatching {
                            info.javaClass.getField("device_scale_factor").setDouble(info, 1.0)
                            info.javaClass.getField("depth").setInt(info, 32)
                            info.javaClass.getField("depth_per_component").setInt(info, 8)
                            info.javaClass.getField("is_monochrome").setBoolean(info, false)
                            listOf("x", "y").forEachIndexed { _, name ->
                                info.javaClass.getField(name).setInt(info, 0)
                            }
                            info.javaClass.getField("width").setInt(info, viewWidth)
                            info.javaClass.getField("height").setInt(info, viewHeight)
                            info.javaClass.getField("available_x").setInt(info, 0)
                            info.javaClass.getField("available_y").setInt(info, 0)
                            info.javaClass.getField("available_width").setInt(info, viewWidth)
                            info.javaClass.getField("available_height").setInt(info, viewHeight)
                            true
                        }.getOrDefault(false)
                    }
                }

                "getScreenPoint" -> args?.getOrNull(1)
                "getDeviceScaleFactor" -> 1.0
                "onPaint" -> {
                    val buffer = args?.getOrNull(3) as? ByteBuffer
                    val width = args?.getOrNull(4) as? Int ?: 0
                    val height = args?.getOrNull(5) as? Int ?: 0
                    if (buffer != null && width > 0 && height > 0) paint(buffer, width, height)
                    null
                }

                else -> defaultReturn(method.returnType)
            }
        }
        return Proxy.newProxyInstance(
            CefRefs.renderHandlerClass.classLoader,
            arrayOf(CefRefs.renderHandlerClass),
            handler,
        )
    }

    private fun paint(buffer: ByteBuffer, width: Int, height: Int) {
        // CEF 给的是整帧 BGRA；直接交给 Skia，避免自己做逐像素翻转。
        val bytes = ByteArray(buffer.remaining())
        buffer.duplicate().get(bytes)
        val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
        runCatching {
            onFrame(Image.makeRaster(info, bytes, width * 4).toComposeImageBitmap())
        }.onFailure { onError(it) }
    }

    private fun defaultReturn(type: Class<*>): Any? = when (type) {
        Void.TYPE -> null
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Double.TYPE -> 0.0
        java.lang.Float.TYPE -> 0f
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Character.TYPE -> '\u0000'
        else -> null
    }
}
