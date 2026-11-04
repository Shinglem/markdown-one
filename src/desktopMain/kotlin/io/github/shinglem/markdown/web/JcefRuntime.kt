package io.github.shinglem.markdown.web

/**
 * 运行期反射探测 JCEF。
 *
 * 库**不**依赖 `org.cef.*`，也不要求构建机装 JBR —— 普通 JDK 上 [isPresent] 为 false，
 * 回落适配器自动不可用，链路退回源码显示。
 *
 * 可用前提（由使用方负责）：
 * 1. classpath 上有 `org.cef.*`（`me.friwi:jcefmaven` 会传递 `jcef-api`；JBR 则用
 *    `--add-modules jcef`）；
 * 2. 有一份 CEF 原生运行时（`jcef_helper.exe` / `libcef.dll` / `jcef.dll` / `resources.pak` /
 *    `locales/`），位置由 `MarkdownConfig.cefNativeDir` 或 `-Dmarkdown.cef.dir` 指定，
 *    也兼容摊在 `java.home/bin` 里的 JBR 布局（见 [CefNativeLocator]）。
 *
 * 探测只解析类与方法（不初始化、不加载 native），因此可以在任何线程安全调用；
 * 真正的 CEF 初始化（`startup` + `getInstance`）推迟到第一次渲染，见 [CefBootstrap]。
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
            CefRefs.startup
            CefRefs.getInstance
            CefRefs.osrCtor
            CefRefs.createImmediately
            true
        }.getOrDefault(false)
    }

    val unavailableReason: String by lazy {
        if (isPresent) {
            ""
        } else {
            "未找到 org.cef（需要 jcef-api 或 JBR with JCEF）"
        }
    }
}
