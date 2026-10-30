package io.github.shinglem.markdown.web

import java.io.File

/**
 * 全局 CEF 引导：`CefApp` 是进程级单例，初始化只做一次，之后所有 [CefEngine] 共享。
 *
 * JBR 自带 JCEF 的初始化链是「两段式」，只在 `getInstance` 之后轮询状态是等不到的：
 *
 * ```
 * CefApp.startup(args)                    ← 异步：加载 libcef/jcef + N_Startup，完成后才会
 *         │                                  complete 内部的 startup future
 *         └─► ourStartupFeature.thenRunAsync(preinit → N_PreInitialize → N_Initialize)
 * ```
 *
 * 也就是说 `getInstance()` 只是把 `preinit` 挂到那个 future 上就返回，`getState()` 一直停在
 * `NEW`；**先调 `startup` 才会真正推进到 `INITIALIZED`**（JBR 25 上实测：不调 → 15 秒 NEW，
 * 调了 → 250ms INITIALIZED，且 native 日志文件才被创建）。
 *
 * 另外两件不设就不出图的：
 * - `browser_subprocess_path`：不指向 JCEF 自带 helper 时，CEF 会把 `--type=gpu-process`
 *   等参数丢给 `java.exe`，子进程报 "Unrecognized option" 后直接退出（GPU/网络进程全崩）；
 * - `createImmediately()`：OSR 浏览器惰性创建，不调就没有渲染进程，`onPaint` 永不来。
 */
internal object CefBootstrap {

    private val lock = Any()

    @Volatile
    private var instance: Any? = null

    /** 首次初始化失败后记住原因：`CefApp` 是进程级单例，重试只会撞 "already initialized" 而掩盖真相。 */
    @Volatile
    private var failure: Throwable? = null

    /** 返回已初始化的 `CefApp`，首次调用会阻塞到 `INITIALIZED`（必须在后台线程调）。 */
    fun app(): Any {
        instance?.let { return it }
        synchronized(lock) {
            instance?.let { return it }
            failure?.let { throw IllegalStateException("CEF 初始化此前已失败：${it.message}", it) }
            return try {
                create().also { instance = it }
            } catch (t: Throwable) {
                failure = t
                throw t
            }
        }
    }

    private fun create(): Any {
        val javaHome = File(System.getProperty("java.home"))

        // 1. 先拉起 CEF 运行时。参数只认 `--framework-dir-path=`（macOS/Linux 定位 libcef），
        //    Windows 上 native 由 java.library.path 解析，传空数组即可。
        val started = CefRefs.startup.invoke(null, emptyArray<String>()) as? Boolean ?: true
        check(started) { "CefApp.startup 返回 false：CEF native 库加载失败" }

        val settings = CefRefs.settingsClass.getDeclaredConstructor().newInstance()
        // OSR 是全局开关，必须在 N_Initialize 之前设好。
        CefRefs.windowlessEnabled.setBoolean(settings, true)
        // 从非标准安装路径/开发环境启动时，CEF 沙箱常常起不来。
        runCatching { CefRefs.noSandbox.setBoolean(settings, true) }
        applyPaths(settings, javaHome)
        applyCache(settings)
        applyLogging(settings)

        // 2. 取单例。注意 `getInstance(CefSettings)` 在已有实例时会抛 IllegalStateException，
        //    所以必须在 [instance] 缓存保护下调用。
        val app = CefRefs.getInstance.invoke(null, settings)
            ?: error("CefApp.getInstance 返回 null")

        // 3. 初始化是异步的，等它真正完成再建浏览器。
        awaitInitialized()
        return app
    }

    /**
     * 只设置**确实存在**的路径：JBR 的 `bin/` 里同时放着 `jcef_helper.exe`、`*.pak`、`locales/`；
     * 自备 CEF 运行时可能完全在别处，这时保持 CEF 的默认推断，别拿错的路径覆盖它。
     */
    private fun applyPaths(settings: Any, javaHome: File) {
        val bin = File(javaHome, "bin")
        helperFile(bin)?.let {
            runCatching { CefRefs.browserSubprocessPath.set(settings, it.absolutePath) }
        }
        if (File(bin, "resources.pak").isFile) {
            runCatching { CefRefs.resourcesDirPath.set(settings, bin.absolutePath) }
        }
        val locales = File(bin, "locales")
        if (locales.isDirectory) {
            runCatching { CefRefs.localesDirPath.set(settings, locales.absolutePath) }
        }
    }

    private fun helperFile(bin: File): File? = listOf("jcef_helper.exe", "jcef_helper")
        .map { File(bin, it) }
        .firstOrNull { it.isFile }

    /**
     * CEF 的 user data 目录带进程级单例锁：两个进程共用默认目录时，后启动的那个会把请求转给
     * 已有实例（日志里是 "Opening in existing browser session."），自己则永远停在 `NEW`。
     * 按 PID 隔离最省心，进程退出后由系统的临时目录清理。
     */
    private fun applyCache(settings: Any) {
        runCatching {
            val dir = File(System.getProperty("java.io.tmpdir"), "markdown-one-cef-${ProcessHandle.current().pid()}")
            if (dir.isDirectory || dir.mkdirs()) CefRefs.cachePath.set(settings, dir.absolutePath)
        }
    }

    private fun applyLogging(settings: Any) {
        runCatching {
            val logFile = File(System.getProperty("java.io.tmpdir"), "markdown-one-cef.log")
            CefRefs.settingsClass.getField("log_file").set(settings, logFile.absolutePath)
            val severity = Class.forName("org.cef.CefSettings\$LogSeverity", false, CefRefs.settingsClass.classLoader)
                .enumConstants
                ?.firstOrNull { (it as Enum<*>).name.contains("INFO") }
            if (severity != null) CefRefs.settingsClass.getField("log_severity").set(settings, severity)
        }
    }

    /** 轮询 `CefApp.getState()` 直到 `INITIALIZED`。 */
    private fun awaitInitialized(timeoutMs: Long = 15_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastState: String? = null
        while (System.currentTimeMillis() < deadline) {
            lastState = runCatching { CefRefs.getState.invoke(null)?.toString() }.getOrNull()
            when (lastState) {
                "INITIALIZED" -> return
                "TERMINATED", "SHUTTING_DOWN" -> error("CEF 已终止（$lastState）")
            }
            Thread.sleep(50)
        }
        val hint = when (lastState) {
            "NEW" -> "；状态停在 NEW 说明 native 从未启动，检查是否需要 --add-modules jcef 与 --enable-native-access=jcef"
            else -> ""
        }
        error("CEF 初始化超时（${timeoutMs}ms），最后状态=$lastState$hint")
    }
}
