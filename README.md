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
              ├── JcefAdapter   (priority  50)  内嵌浏览器离屏渲染（可选 artifact）
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
- `:jcef` 是 `kotlin("jvm")` 子项目，天然不参与其它 target。

`LRU` 缓存等 JVM 专有实现也集中在 `desktopMain`，加 target 时按平台各写一份。

## 目录

```
markdown-one/
├── build.gradle.kts        ← 核心库（KMP）
├── src/commonMain/         ← 模型 / 解析 / 适配器 SPI / 内置适配器
├── src/desktopMain/        ← ServiceLoader 发现、平台实现
├── jcef/                   ← 可选 artifact：JCEF 适配器（未实现）
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
```

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

库**不内置** JCEF 适配器，原因有两个：`jcef.jmod` 175 MB + `libcef.dll` 262 MB 不该由每个使用者背；
是否带 Chromium 属于分发决策。需要时请自己实现，并注意：

1. **运行时**：必须用 **JBR with JCEF**（或自备 CEF 运行时），启动参数加 `--add-modules jcef`。
   标准 OpenJDK 上没有 `org.cef`，`isAvailable()` 要据此返回 false，让链路自动降级。
2. **编译期**：`jcef.jmod` 不能直接进 classpath，需 `jmod extract` 出 classes 打成 jar 后
   `compileOnly` 引入。
3. **三个必须自己处理的点**（本设计原本要做的，也就是真正的难点）：
   - **有界高度**：OSR 的 `CefRenderHandler.onPaint` 拿不到高度就不会绘制，
     LazyColumn 里必须由内容回传 `scrollHeight` 再设 `Modifier.height(..)`；
   - **实例池**：每个 `CefBrowser` 是独立 Chromium 渲染进程，一块一个会瞬间打爆内存；
   - **网络白名单**：LLM 输出里的 `<img src="http://...">` 是追踪像素，必须在
     `CefRequestHandler.onBeforeResourceLoad` 里拦掉，并关掉 `file_access` / `local_storage`。

参考：`AdapterKind.Jcef` 只是给诊断面板用的标签，不影响路由 —— 流式期间 kind 为 `Jcef`
的适配器会被自动跳过，避免每帧重载浏览器内容。

## 许可证

[Apache-2.0](LICENSE)。

