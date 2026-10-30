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

        browser = CefRefs.osrCtor.newInstance(client, "about:blank", requestContext, newRenderHandler())
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
        val encoded = Base64.getEncoder().encodeToString(html.toByteArray(Charsets.UTF_8))
        runCatching { CefRefs.loadURL.invoke(browser, "data:text/html;base64,$encoded") }
    }

    /** 关闭本引擎的浏览器与 client；`CefApp` 是全局单例，不能在这里关。 */
    fun dispose() {
        runCatching { CefRefs.close.invoke(browser, true) }
        runCatching { CefRefs.dispose.invoke(client) }
    }

    /** `CefRenderHandler` 的动态实现：只关心 `getViewRect` 与 `onPaint`。 */
    private fun newRenderHandler(): Any {
        val handler = java.lang.reflect.InvocationHandler { _, method, args ->
            when (method.name) {
                "getViewRect" -> Rectangle(0, 0, viewWidth, viewHeight)
                "getScreenInfo" -> false
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
        }
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
