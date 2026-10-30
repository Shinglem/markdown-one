package io.github.shinglem.markdown.web

/**
 * 运行期反射探测 JCEF。
 *
 * 库**不**依赖 `org.cef.*`，也不要求构建机装 JBR —— 普通 JDK 上 [isPresent] 为 false，
 * 回落适配器自动不可用，链路退回源码显示。
 *
 * 可用前提（由使用方负责）：
 * 1. 运行时是 **JBR with JCEF**（或自备 CEF 运行时）；
 * 2. 启动参数带 `--add-modules jcef`。
 *
 * 探测只解析类与方法（不初始化、不加载 native），因此可以在任何线程安全调用；
 * 真正的 CEF 初始化推迟到第一次渲染。
 */
internal object JcefRuntime {

    val isPresent: Boolean by lazy {
        runCatching {
            CefRefs.appClass
            CefRefs.settingsClass
            CefRefs.clientClass
            CefRefs.browserClass
            CefRefs.osrClass
            CefRefs.renderHandlerClass
            CefRefs.getInstance
            CefRefs.osrCtor
            true
        }.getOrDefault(false)
    }

    val unavailableReason: String by lazy {
        if (isPresent) {
            ""
        } else {
            "未找到 org.cef（需要 JBR with JCEF，且启动参数带 --add-modules jcef）"
        }
    }
}
