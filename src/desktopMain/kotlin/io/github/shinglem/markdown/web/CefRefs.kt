package io.github.shinglem.markdown.web

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * 反射句柄缓存：`Class` / `Method` / `Constructor` 只解析一次，之后全局复用。
 *
 * 为什么不换 `MethodHandle`：JDK 18+（JEP 416）的 `Method.invoke` 内部已经是 method handle 调用，
 * 热路径性能同量级；而这里的调用频次是「每个引擎一次 / 内容变化一次」，
 * 真正的成本在 CEF 初始化（秒级）与渲染进程，方法调用开销可忽略。
 * 值得优化的是**避免重复查找**，也就是这个对象存在的意义。
 *
 * 全部用 `initialize = false` 解析：`getMethod` 不触发类初始化。
 */
internal object CefRefs {
    private val loader = CefRefs::class.java.classLoader

    val appClass: Class<*> by lazy { Class.forName("org.cef.CefApp", false, loader) }
    val settingsClass: Class<*> by lazy { Class.forName("org.cef.CefSettings", false, loader) }
    val clientClass: Class<*> by lazy { Class.forName("org.cef.CefClient", false, loader) }
    val browserClass: Class<*> by lazy { Class.forName("org.cef.browser.CefBrowser", false, loader) }
    val osrClass: Class<*> by lazy { Class.forName("org.cef.browser.CefBrowserOsrWithHandler", false, loader) }
    val requestContextClass: Class<*> by lazy { Class.forName("org.cef.browser.CefRequestContext", false, loader) }
    val renderHandlerClass: Class<*> by lazy { Class.forName("org.cef.handler.CefRenderHandler", false, loader) }

    val windowlessEnabled: Field by lazy { settingsClass.getField("windowless_rendering_enabled") }
    val noSandbox: Field by lazy { settingsClass.getField("no_sandbox") }
    val browserSubprocessPath: Field by lazy { settingsClass.getField("browser_subprocess_path") }
    val resourcesDirPath: Field by lazy { settingsClass.getField("resources_dir_path") }
    val localesDirPath: Field by lazy { settingsClass.getField("locales_dir_path") }
    val cachePath: Field by lazy { settingsClass.getField("cache_path") }

    /** `CefApp.startup(String[])`：**不调它 CEF 永远不会初始化**（详见 [CefBootstrap]）。 */
    val startup: Method by lazy { appClass.getMethod("startup", Array<String>::class.java) }
    val getInstance: Method by lazy { appClass.getMethod("getInstance", settingsClass) }
    val getState: Method by lazy { appClass.getMethod("getState") }
    val createClient: Method by lazy { appClass.getMethod("createClient") }
    val getGlobalContext: Method by lazy { requestContextClass.getMethod("getGlobalContext") }

    val osrCtor: Constructor<*> by lazy {
        osrClass.getConstructor(clientClass, String::class.java, requestContextClass, renderHandlerClass)
    }

    /** OSR 浏览器是惰性创建的：不调它就没有渲染进程，也就没有 `onPaint`。 */
    val createImmediately: Method by lazy { browserClass.getMethod("createImmediately") }
    val loadURL: Method by lazy { browserClass.getMethod("loadURL", String::class.java) }

    /**
     * 一律从 `CefBrowser` **接口**取方法，不要用实现类 `CefBrowser_N`：
     * 后者虽然 `public`，但 JBR 的 jcef 模块不导出它的访问权（实测 `IllegalAccessException`）。
     */
    val wasResized: Method by lazy {
        browserClass.getMethod("wasResized", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
    }
    val setFocus: Method by lazy { browserClass.getMethod("setFocus", Boolean::class.javaPrimitiveType) }
    val close: Method by lazy { browserClass.getMethod("close", Boolean::class.javaPrimitiveType) }
    val dispose: Method by lazy { clientClass.getMethod("dispose") }
}
