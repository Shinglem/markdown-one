# markdown-one

面向 Compose Multiplatform 的 Markdown 渲染组件：**解析 → 块 → 适配器 → 渲染**，
一条能力不足就降级下一级，最后兜底成源码。

**目标多端，当前只实现 desktop（JVM）。**

## 状态

| 项 | 状态 |
|---|---|
| desktop（JVM） | ✅ 可用 |
| Android / iOS / Web | 结构已预留（见「多端」），未实现 |
| 发布 | 未发布到 Maven Central；当前以 git submodule 或本地构建接入 |

## 为什么是「适配器」

Markdown 里塞得下代码、图表、公式、原始 HTML —— 它们的渲染手段完全不同，
但调用方只想调一个 `MarkdownText`。

于是每种内容类型先被解析成块（`MdBlock`），再按 **格式** 向适配器链要渲染器：

```
MdBlock ──► AdapterRegistry
              ├── NativeAdapter (priority 100)  纯 Compose 绘制
              ├── JcefAdapter   (priority  50)  内嵌浏览器离屏渲染（反射接入，零编译期依赖）
              └── SourceFallback(priority   0)  源码 + 提示，永不留空白
```

- **一个适配器可以声明多种格式**（`formats(): Set<FormatId>`），也支持前缀通配（`code:*`）。
- **自动发现**：可选 artifact 通过 `META-INF/services` 登记 `BlockAdapterFactory`，存在即启用。
- **显式注册优先**：`MarkdownConfig.adapters` 可覆盖/禁用内置适配器。
- **两条纪律**：禁止静默替换（画不出就走下一级并留可见痕迹）；流式期间只降不升（JCEF 不参与）。

内置适配器：

| id | 格式 | 实现 |
|---|---|---|
| `builtin.markdown` | `markdown` | mikepenz 的 element composable |
| `builtin.code` | `code:*` | Highlights + 8 组正则补丁 |
| `builtin.mermaid` | `mermaid` / `code:mermaid` | `io.github.swithun-liu:mermaid-compose`（纯 Kotlin，无 WebView） |
| `builtin.source` | `*` | 源码兜底 |

## 用法

```kotlin
MarkdownText(
    markdown = text,
    streaming = isAnswering,   // 流式：图表先占位，未闭合栅栏会被补齐
    config = MarkdownConfig(), // 适配器、文案、诊断回调
)
```

## 构建契约（改动前务必先读）

本仓库会被宿主工程以 **git submodule** 挂载（宿主 `include(":modules:markdown-one")`），
那时本仓库的 `settings.gradle.kts` 不生效，`libs` 指向宿主的 version catalog。因此：

1. **target 名保持 `jvm("desktop")`**：同一构建内的 KMP project 依赖要求 target 名对齐，
   改成标准 `jvm()` 会让宿主变体不匹配。多端化时需与宿主同步改。
2. **`jvmTarget = JVM_25`**，与宿主一致。
3. **插件用 version catalog alias，依赖坐标用 `build.gradle.kts` 里的字面量**
   —— 宿主 toml 里没有的 catalog 条目会让脚本在宿主构建里直接报错。
4. **发布插件用 `id("maven-publish")`**（builtin，无版本），不要用 alias。
5. **镜像按属性切换**：本机默认 `mirror=cn`（腾讯云 → 阿里云 → central 兜底）；
   CI 传 `-Pmirror=off` 只用官方源，避免境外构建机撞国内镜像超时。

## 多端

全部逻辑在 `commonMain`，只有两处平台差异：

- `discoverAdapterFactories()` 是 `expect/actual`：JVM 用 `ServiceLoader`，
  其它端返回空列表（靠 `MarkdownConfig.adapters` 显式注册）。
- JCEF 回落适配器在 `desktopMain` 里**全程反射**，对 `org.cef.*` 零编译期依赖，
  因此在任何 JDK 上都能构建，其它 target 也不会看到它。

`LRU` 缓存等 JVM 专有实现也集中在 `desktopMain`，加 target 时按平台各写一份。

## 目录

```
markdown-one/
├── build.gradle.kts        ← 核心库（KMP）
├── src/commonMain/         ← 模型 / 解析 / 适配器 SPI / 内置适配器
├── src/desktopMain/        ← ServiceLoader 发现、JCEF 反射适配器（web/）
└── demo/                   ← Compose Desktop sandbox
```

## 依赖

| 用途 | 坐标 | 版本 |
|---|---|---|
| GFM 解析 | `org.jetbrains:markdown` | 0.7.3 |
| 块渲染 | `com.mikepenz:multiplatform-markdown-renderer(-m3/-code)` | 0.46.0 |
| 代码高亮 | `dev.snipme:highlights` | 1.1.0 |
| 图片 | `io.coil-kt.coil3:coil-compose` / `coil-svg` | 3.6.3 |
| 图表 | `io.github.swithun-liu:mermaid-compose` | 0.1.21 |

版本只在 `build.gradle.kts` 顶部出现一次。

## 开发

```bash
./gradlew :desktopTest   # 单元测试
./gradlew :demo:run      # sandbox

# 指定启动时选中的 fixture（序号或名称）
./gradlew :demo:run -Pfixture=0

# 用 JBR with JCEF 跑（自动加 --add-modules jcef 等参数），验证 JCEF 回落
./gradlew :demo:run -PjbrHome="C:\path\to\jbrsdk_jcef" -Pfixture=6
```

demo 左侧是 fixture 列表，右侧「适配器路由」实时显示**每个块命中的适配器**，
是这套架构最有效的自检工具。`复杂综合文档` 覆盖了标题（含 setext）、行内格式、
嵌套列表与任务列表、表格、四种语言代码块、flowchart 与 sequence 图、公式、原始 HTML、
嵌套引用、分割线与长段落。

## 扩展：接入你自己的适配器

库只内置纯 Compose 的适配器。任何需要平台能力（内嵌浏览器、原生控件）的内容，
由调用方自己实现 [BlockAdapter] 并通过 `MarkdownConfig.adapters` 注册。

### 最小实现

```kotlin
class MyMathAdapter : BlockAdapter {
    override val id = "my.math"
    override val kind = AdapterKind.Native
    override val priority = 120                  // 高于内置的 100 即接管
    override fun formats() = setOf(Formats.Math) // 也可声明多种格式
    override fun isAvailable() = true
    override fun canRender(block: MdBlock) = block is MdBlock.Math

    @Composable
    override fun Render(block: MdBlock, modifier: Modifier) {
        val math = block as MdBlock.Math
        // 画不出来时自行显示 math.raw —— 库的兜底只在「没有适配器命中」时生效
    }
}

MarkdownText(markdown, config = MarkdownConfig(adapters = listOf(MyMathAdapter())))
```

自动发现也可用：在可选 artifact 里放
`META-INF/services/io.github.shinglem.markdown.adapter.BlockAdapterFactory`，实现 `BlockAdapterFactory`，
依赖在 classpath 上就会生效（JVM/desktop 端）。

### 用 JCEF 兜底原生画不出的内容

`WebFallback.Jcef` 打开后，`builtin.jcef` 会用 **JCEF 离屏渲染（OSR）** 把块画出来，
再把 BGRA 帧交给 Skia。库对 `org.cef.*` **没有编译期依赖**（全程反射），
所以普通 JDK 上照常构建，运行时找不到 `org.cef` 就自动退回 `builtin.source`。

**运行时要求**

1. **JBR with JCEF**（或自备 CEF 运行时）；
2. 启动参数 `--add-modules jcef --enable-native-access=jcef`
   （后者是 JDK 24+ 加载 native 的许可，将来会强制）；
   demo 传 `-PjbrHome=<JBR 路径>` 会自动带上。

**JBR 上把 CEF 拉起来要做的四件事**（[CefBootstrap.kt](src/desktopMain/kotlin/io/github/shinglem/markdown/web/CefBootstrap.kt) 里都做了，
列出来是因为每一条都对应一次「什么都没发生」的静默失败）：

| 步骤 | 不做的后果 |
|---|---|
| `CefApp.startup(new String[0])` **必须先于** `getInstance` | `getState()` 永远停在 `NEW`。JBR 的 `getInstance` 只是把初始化挂在一个内部 future 上，**只有 `startup` 才会完成它**；CEF 自己的 native 日志也不会生成。 |
| `settings.browser_subprocess_path` 指向 `<java.home>/bin/jcef_helper.exe` | CEF 把 `--type=gpu-process` 传给 `java.exe`，子进程报 `Unrecognized option` 后退出，GPU/网络进程全崩。 |
| `settings.cache_path` 按进程隔离 | 两个进程共用默认 user data 目录时，后启动的会连到已有实例（日志：`Opening in existing browser session.`）并卡在 `NEW`。 |
| `browser.createImmediately()` | OSR 浏览器是惰性创建的，不调就没有渲染进程，`onPaint` 永远不来。 |

反射还有一条硬规矩：**方法一律从 `CefBrowser` 接口上取**，别用实现类 `CefBrowser_N` ——
后者虽然是 `public`，在 jcef 模块里却不可访问，会抛 `IllegalAccessException`。

还有一个只在长文档里才暴露的坑：`CefRenderHandler.getScreenInfo` **必须填充 `CefScreenInfo`
并返回 `true`**。返回 `false` 时 CEF 认为该浏览器没有可用屏幕，`onPaint` 一帧都不会来，
表现就是「占据了一块高度但永远空白」，日志里也没有任何报错。

**仍然要自己处理的点**

- **有界高度**：OSR 先有尺寸才出帧，适配器按 `MarkdownConfig.webFallbackHeight` 定高，
  内容在页面内滚动；
- **实例池**：每个 `CefBrowser` 是独立的渲染进程，长文档里一块一个会吃很多内存；
  用 `webDocument` 收窄回落范围，而不是把整个文档都交给浏览器；
- **网络白名单**：LLM 输出里的 `<img src="http://...">` 是追踪像素，需要自己拦
  （本库尚未内置请求拦截）。

`AdapterKind.Jcef` 同时是诊断面板上的标签：流式期间 kind 为 `Jcef` 的适配器会被跳过，
避免每帧重载 Chromium。

## 许可证

[Apache-2.0](LICENSE)。

