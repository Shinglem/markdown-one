package io.github.shinglem.markdown.web

import java.io.File

/**
 * 全局 CEF 引导：`CefApp` 是进程级单例，初始化只做一次，之后所有 [CefEngine] 共享。
 *
 * 初始化链（java-cef 135 实测，普通 JDK 与 JBR 都是这套源码）：
 *
 * ```
 * CefApp.startup(args)     ← Windows 上什么都不做（直接 return true），只在 Linux/macOS 载库
 * getInstance(args, settings)  ← 构造器里 SystemBootstrap.loadLibrary(jawt/chrome_elf/libcef/jcef)，
 *         │                     然后把 state 置 NEW —— 此时 CEF **还没初始化**
 * createClient()           ← state == NEW 时才真正跑 N_PreInitialize + N_Initialize
 *         └─► state 变 INITIALIZED，之后才能建浏览器
 * ```
 *
 * 也就是说**只调 getInstance 会永远停在 NEW**：必须有 `createClient()`（或 JBR 上 startup 推进）
 * 才会初始化。见 [ensureInitializing]。
 *
 * 另外两件不设就不出图的：
 * - `browser_subprocess_path`：不指向 JCEF 自带 helper 时，CEF 会把 `--type=gpu-process`
 *   等参数丢给 `java.exe`，子进程报 "Unrecognized option" 后直接退出（GPU/网络进程全崩）；
 * - `createImmediately()`：OSR 浏览器惰性创建，不调就没有渲染进程，`onPaint` 永不来。
 *
 * 目录不写死：由 [CefNativeLocator] 按「显式配置 → 系统属性 → `java.home/bin`」解析，
 * 因此普通 JDK + 自带 CEF bundle 也能跑，不依赖 JBR。
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
        val nativeDir = CefNativeLocator.resolve()
        prepareNative(nativeDir)

        // 1. 先调 startup。Windows 上它是个空操作，但 Linux/macOS 靠它载库；
        //    两个平台都保持"先 startup 再 getInstance"的顺序，别按平台分支。
        val started = CefRefs.startup.invoke(null, emptyArray<String>()) as? Boolean ?: true
        check(started) { "CefApp.startup 返回 false：CEF native 库加载失败${pathHint(nativeDir)}" }

        val settings = CefRefs.settingsClass.getDeclaredConstructor().newInstance()
        // OSR 是全局开关，必须在 N_Initialize 之前设好。
        CefRefs.windowlessEnabled.setBoolean(settings, true)
        // `no_sandbox` 是 JBR 打的补丁字段，jcefmaven 的 jcef-api 没有；缺了就用命令行开关兜底。
        runCatching { CefRefs.noSandbox.setBoolean(settings, true) }
        applyPaths(settings, nativeDir)
        applyCache(settings)
        applyLogging(settings)

        // 2. 取单例。注意 `getInstance` 在已有实例时会抛 IllegalStateException，
        //    所以必须在 [instance] 缓存保护下调用。
        val app = getInstance(settings)
        // 3. 触发真正的初始化，再等它完成。
        ensureInitializing(app)
        awaitInitialized()
        return app
    }

    /**
     * 让 CEF 自己的加载器按**绝对路径**加载 `chrome_elf` / `libcef` / `jcef`。
     *
     * 为什么需要：`SystemBootstrap.loadLibrary(name)` 走的是 `ClassLoader.loadLibrary`，
     * 它先在 `java.library.path` 里**找文件**，找不到直接抛 `UnsatisfiedLinkError`
     * —— 所以光 `System.load(绝对路径)` 预加载也没用，它会重新按名字找一遍。
     * 注入 loader 后彻底绕开 `java.library.path`：CEF 可以放在任意目录。
     *
     * `jawt` 是 JVM 自带的（`java.home/bin`），CEF 目录里没有时回退到那里。
     */
    private fun prepareNative(dir: File?) {
        if (dir == null) return
        // 目录就是 `java.home/bin` 时不插手：那是 JBR with JCEF 由模块系统自己加载的旧路径。
        if (sameDir(dir, CefNativeLocator.javaHomeBin())) return
        if (installAbsoluteLoader(dir)) return
        // 兜底：SystemBootstrap 不可用时按依赖顺序手动预加载。这条路径还要求
        // `java.library.path` 含该目录（CEF 仍会按名字找一遍），所以只当降级方案。
        CefNativeLocator.preloadOrder(dir).forEach { lib ->
            try {
                System.load(lib.absolutePath)
            } catch (t: Throwable) {
                throw IllegalStateException("加载 ${lib.name} 失败（目录 ${dir.absolutePath}）：${t.message}", t)
            }
        }
    }

    /** @return 是否成功注入 [CefRefs.setLoader]。 */
    private fun installAbsoluteLoader(dir: File): Boolean {
        val loaderType = CefRefs.systemBootstrapLoader ?: return false
        val setLoader = CefRefs.setLoader ?: return false
        val loader = java.lang.reflect.Proxy.newProxyInstance(loaderType.classLoader, arrayOf(loaderType)) { proxy, method, args ->
            when (method.name) {
                "loadLibrary" -> {
                    loadByName(dir, (args?.firstOrNull() as? String).orEmpty())
                    null
                }
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "CefNativeLoader(${dir.absolutePath})"
                else -> null
            }
        }
        return runCatching {
            setLoader.invoke(null, loader)
            true
        }.getOrDefault(false)
    }

    private fun loadByName(dir: File, name: String) {
        if (name.isBlank()) throw UnsatisfiedLinkError("CEF 请求加载空库名")
        val mapped = System.mapLibraryName(name)
        val file = listOfNotNull(File(dir, mapped), CefNativeLocator.javaHomeBin()?.let { File(it, mapped) })
            .firstOrNull { it.isFile }
            ?: throw UnsatisfiedLinkError("找不到 $mapped（CEF 目录：${dir.absolutePath}）")
        System.load(file.absolutePath)
    }

    private fun getInstance(settings: Any): Any {
        // `--no-sandbox`：从任意安装路径/开发环境启动时 CEF 沙箱常常起不来。
        CefRefs.getInstanceWithArgs?.let { withArgs ->
            return withArgs.invoke(null, arrayOf("--no-sandbox"), settings) as? Any
                ?: error("CefApp.getInstance 返回 null")
        }
        return CefRefs.getInstance.invoke(null, settings) ?: error("CefApp.getInstance 返回 null")
    }

    /**
     * `getInstance` 之后 state 是 `NEW`，**还没有初始化**：`createClient()` 才会触发
     * `N_PreInitialize` + `N_Initialize`。JBR 的补丁版可能已经由 startup 推进过，所以先看一眼状态。
     */
    private fun ensureInitializing(app: Any) {
        if (stateName() != "NEW") return
        runCatching { CefRefs.createClient.invoke(app) }
    }

    /**
     * 只设置**确实存在**的路径：不同发行包的 CEF 目录布局略有差异（JBR 的 `bin/` 里同时放着
     * `jcef_helper.exe`、`*.pak`、`locales/`；自备 bundle 可能少了某个），
     * 缺哪个就保持 CEF 的默认推断，别拿错的路径覆盖它。
     */
    private fun applyPaths(settings: Any, dir: File?) {
        if (dir == null) return
        CefNativeLocator.helper(dir)?.let {
            runCatching { CefRefs.browserSubprocessPath.set(settings, it.absolutePath) }
        }
        if (File(dir, "resources.pak").isFile) {
            runCatching { CefRefs.resourcesDirPath.set(settings, dir.absolutePath) }
        }
        val locales = File(dir, "locales")
        if (locales.isDirectory) {
            runCatching { CefRefs.localesDirPath.set(settings, locales.absolutePath) }
        }
    }

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

    private fun pathHint(dir: File?): String = if (dir != null) {
        "（CEF 目录：${dir.absolutePath}）"
    } else {
        val tried = CefNativeLocator.candidates().joinToString("、") { it.absolutePath }.ifEmpty { "无" }
        "（没找到 CEF native，已尝试：$tried；用 -D${CefNativeLocator.SYSPROP}=<dir> " +
            "或 MarkdownConfig.cefNativeDir 指定目录）"
    }

    private fun stateName(): String? = runCatching { CefRefs.getState.invoke(null)?.toString() }.getOrNull()

    private fun sameDir(a: File, b: File?): Boolean {
        if (b == null) return false
        return runCatching { a.canonicalFile == b.canonicalFile }.getOrDefault(a.absoluteFile == b.absoluteFile)
    }

    /** 轮询 `CefApp.getState()` 直到 `INITIALIZED`。 */
    private fun awaitInitialized(timeoutMs: Long = 15_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastState: String? = null
        while (System.currentTimeMillis() < deadline) {
            lastState = stateName()
            when (lastState) {
                "INITIALIZED" -> return
                "TERMINATED", "SHUTTING_DOWN" -> error("CEF 已终止（$lastState）")
            }
            Thread.sleep(50)
        }
        val hint = when (lastState) {
            "NEW" -> "；状态停在 NEW 说明初始化没被触发或 native 没加载成功，" +
                "检查 CEF 目录是否由 -D${CefNativeLocator.SYSPROP} 或 MarkdownConfig.cefNativeDir 指对"
            else -> ""
        }
        error("CEF 初始化超时（${timeoutMs}ms），最后状态=$lastState$hint")
    }
}
