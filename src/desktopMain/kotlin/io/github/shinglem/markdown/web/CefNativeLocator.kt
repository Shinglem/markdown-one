package io.github.shinglem.markdown.web

import java.io.File

/**
 * CEF 原生运行时（`libcef.dll` / `jcef.dll` / `jcef_helper.exe` / `resources.pak` / `locales/`）
 * 的目录解析。
 *
 * 解析顺序（先命中先用）：
 * 1. [explicitDir] —— 调用方用 `MarkdownConfig.cefNativeDir` 显式指定；
 * 2. 系统属性 `-Dmarkdown.cef.dir=<dir>`；环境变量 `MARKDOWN_CEF_DIR` —— 开发态与排障时覆盖用；
 * 3. `java.home/bin` —— **仅作兼容兜底**（JBR with JCEF 把 CEF 直接摊在 JDK 的 `bin` 里）。
 *
 * 前两级让 CEF 与 JVM 安装目录**彻底解耦**：可以把 bundle 放在应用自己的 `cef/`
 * 或构建产物目录里，不需要 JVM 目录可写，也不需要 `jcef.dll` 出现在 `java.library.path` 上
 * （CEF 自己的加载器由 `CefBootstrap` 换成按绝对路径加载）。
 *
 * 平台约定：本库的宿主是 Windows 桌面端，只识别 `.dll` / `jcef_helper.exe` 的平铺布局。
 * macOS 的 CEF 在 `.framework` 里、helper 是 `jcef Helper.app`，布局不同，需要显式指定且
 * 自行把 framework 加入 `DYLD_FRAMEWORK_PATH`（未在本库范围内）。
 */
internal object CefNativeLocator {

    /** 覆盖用系统属性。 */
    const val SYSPROP: String = "markdown.cef.dir"

    /** 覆盖用环境变量（测试与脚本里比 `-D` 好传）。 */
    const val ENV: String = "MARKDOWN_CEF_DIR"

    /** 显式目录，由 `JcefAdapter` 从 `MarkdownConfig.cefNativeDir` 写入。 */
    @Volatile
    var explicitDir: File? = null

    /** 候选目录（按优先级去重），排障信息用。 */
    fun candidates(): List<File> = buildList {
        explicitDir?.let { add(it) }
        System.getProperty(SYSPROP)?.takeIf { it.isNotBlank() }?.let { add(File(it)) }
        System.getenv(ENV)?.takeIf { it.isNotBlank() }?.let { add(File(it)) }
        javaHomeBin()?.let { add(it) }
    }.distinct()

    /** 第一个真正含 CEF native 的目录；都没有返回 null。 */
    fun resolve(): File? = candidates().firstOrNull { containsNative(it) }

    /** JDK 自己的 `bin`：兼容 JBR with JCEF 的旧布局。 */
    fun javaHomeBin(): File? =
        System.getProperty("java.home")?.takeIf { it.isNotBlank() }?.let { File(it, "bin") }

    /**
     * 目录里是否有 CEF native。
     *
     * 判据放宽到「helper 或任一共享库存在」：不同发行包（JBR 的 jcef 模块、jcefmaven 的
     * bundle）布局略有差异，只认一个文件会误判成「没有」而退回默认推断。
     */
    fun containsNative(dir: File): Boolean =
        dir.isDirectory && (helper(dir) != null || nativeLibs(dir).isNotEmpty())

    fun helper(dir: File): File? = listOf("jcef_helper.exe", "jcef_helper")
        .map { File(dir, it) }
        .firstOrNull { it.isFile }

    /**
     * 需要**按顺序** `System.load` 的原生库：`libcef` 必须先于 `jcef`。
     *
     * `jcef.dll` 静态依赖 `libcef.dll`，而 Windows 的 DLL 搜索顺序里**没有**「jcef.dll 自己
     * 所在目录」，所以只 `System.load(jcef.dll)` 会因为找不到 libcef 而 `UnsatisfiedLinkError`。
     * 先显式载入 libcef 后，jcef 的导入解析会命中已加载的同名模块。
     */
    fun nativeLibs(dir: File): List<File> = listOf("libcef", "jcef").mapNotNull { base ->
        libFileNames(base).map { File(dir, it) }.firstOrNull { it.isFile }
    }

    /**
     * `SystemBootstrap` 不可用时的兜底预加载顺序（依赖在前）：
     * `jawt`（JVM 自带，在 `java.home/bin`）→ `chrome_elf` → `libcef` → `jcef`。
     *
     * 实测：`libcef.dll` 静态导入 `chrome_elf.dll`、`jcef.dll` 静态导入 `jawt.dll`，
     * 缺一个就整体 `UnsatisfiedLinkError`（且报错只说 "Can't find dependent libraries"）。
     */
    fun preloadOrder(dir: File): List<File> = buildList {
        javaHomeBin()?.let { bin ->
            libFileNames("jawt").map { File(bin, it) }.firstOrNull { it.isFile }?.let(::add)
        }
        listOf("chrome_elf", "libcef", "jcef").forEach { base ->
            libFileNames(base).map { File(dir, it) }.firstOrNull { it.isFile }?.let(::add)
        }
    }

    /** 平台库文件名（`internal` 供测试构造样本文件）。 */
    internal fun libFileNames(base: String): List<String> = when {
        // System.mapLibraryName 在 Linux 上会给 "libcef" 再加一次 lib 前缀，所以自己拼。
        osName.contains("win") -> listOf("$base.dll")
        osName.contains("mac") -> listOf("lib$base.dylib", "$base.dylib")
        else -> listOf("lib$base.so")
    }

    private val osName: String = System.getProperty("os.name").orEmpty().lowercase()
}
