# 变更日志

格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased]

尚未发布到 Maven Central，当前以 git submodule / 本地构建接入。

### 修复

- 行内构建器在 `MarkdownTokenTypes.TEXT` 上不做 `$…$` 文本扫描：
  `CODE_SPAN` 的子节点同样是 `TEXT`，扫描会把行内代码里的 `$x$` 渲染成公式。

### 变更

- **嵌套在引用 / 列表里的块交回适配器链**：`text/MdBlocks.kt` 的 `MdRoutedBlock` 不再用写死的
  `when` 处理嵌套块，而是复用 `parse/MdBlockFactory`（解析器与渲染层共用一份 AST → `MdBlock` 映射）
  与同一个 `AdapterRegistry`。因此：
  - 引用里的 `$$…$$` 会真正排版（`BLOCK_QUOTE > PARAGRAPH > BLOCK_MATH` 这一层也会被拆开处理）；
  - 列表项里的 mermaid 会出图（以前退化成普通代码块）；
  - 嵌套表格 / 分割线 / HTML 块与顶层表现一致；
  - 全落空时由 `builtin.source` 兜底显示源码，绝不静默丢内容。
- **引用内块级公式的 TeX 会剥掉 `>` 前缀**：`BLOCK_MATH` 节点在引用里会把行首的块引用标记圈进
  offset，直接取 raw 会带着 `>` 去排版，必然失败退化成源码（`MdBlockFactory.mathTex`）。
- **移除 `com.mikepenz:multiplatform-markdown-renderer*` 全部 4 个依赖**，文本与块渲染改为自研
  `io.github.shinglem.markdown.text` 包：
  - `MdInline`：一次走完 AST 生成 `AnnotatedString`（strong / emph / 删除线 / 高亮 / 行内码 /
    链接 / 自动链接 / 引用式链接 / 图片 / 软硬换行 / 转义），顺手处理公式与图片的 inline content；
  - `MdBlocks` / `MdTable` / `MdCodeBlock`：段落、标题（ATX 与 setext）、引用、列表（含任务列表）、
    表格、分割线、代码块外壳；
  - `MdColors` / `MdTypography` / `MdDimens` / `MdPadding`：替换主题里第三方 renderer 的模型，
    `outsidePaperTheme()` 与 `markdownMaterialTheme()` 语义不变。
  收益：渲染层再也没有第三方控件的黑盒，块的粒度、间距与降级路径都由自己掌握。
- `builtin.markdown` 由 `TextBlockAdapter` 接管（id、kind、priority 不变，宿主配置与诊断面板不受影响）。
- 引用式链接（`[文字][label]`）现在会扫描全文的链接定义并真正跳转；以前只能显示成源码。
- `SelectionContainer` 由 `MarkdownText` 外层统一包一层，跨块选区不再断成若干段。
- **公式引擎换成 `io.github.huarangmeng:latex-renderer:1.6.3`**（MIT，Compose 原生矢量绘制 +
  KaTeX 字体），删除 JLaTeXMath 与自建的位图缓存（`MathImage` / `MathRenderCache`）。
  行内与块级共用 `rememberLatexConfig`，字号取正文字号、颜色取正文色。
  收益：高分屏清晰、`\Omega` 等大写希腊字母正常（JLaTeXMath 在 JDK 25+ 渲染空白，
  见 opencollab/jlatexmath#111）、语法覆盖更全（`pmatrix` / `align` / `mhchem` 等）。

### 已知问题

- 块级公式由 `Latex()` 异步解析，首帧可能空白一两帧；行内公式是同步测量，不受影响。
- 无法识别时由引擎自身把未知命令标红输出，`mathFallbackText` 只在测量失败（空输入等）时使用。

### 行为变更

- **行内公式的识别口径与上游 0.7.17 对齐：`$` 前必须是空白或行首**。
  紧贴中文标点/汉字的 `$…$`（如 `时，$\bar{x}$`）不再渲染成公式，按源码显示 ——
  这与 GitHub/KaTeX 的宽松规则不同，是刻意选择（上游 #196 为修 #179 的货币误判引入）。
  为此删除了 `parse/InlineMathRepair`（原先会把这批 `$…$` 补成 `INLINE_MATH`）。
- 行内构建器显式输出 `GFMTokenTypes.DOLLAR`：未识别成公式的 `$` 如果不输出就会被静默丢弃
  （`时，$x$` 会渲染成 `时，x`）。
- **链接点击只回调 `MarkdownConfig.onLinkClick`，库自身不再打开任何 URI**。
  此前走的是第三方 renderer 的 `LocalUriHandler`，会绕过宿主的拦截直接拉起系统浏览器，
  与 SECURITY.md 的威胁模型不符。
- 视觉差异（相对第三方 renderer）：表格列宽改为按可用宽度均分、单元格允许折行（不再省略号截断）；
  任务列表复选框改成 14dp 的小方框（不再占 48dp 触控区）；代码块顶栏的语言标签在左、复制按钮在右。
- `org.jetbrains:markdown` 的声明版本改为 **0.7.17**（原先写 0.7.3，实际被第三方 renderer 传递升级到
  0.7.17）：两者的 `$` 识别规则不同 —— 0.7.1–0.7.3 不要求左界符前有空白，0.7.17 要求。
- 构建脚本加 `@file:Suppress("DEPRECATION")`：Gradle 9.8 + CMP 1.12 起 `compose.runtime` 等
  便捷 DSL 被标记为 deprecated，而 Kotlin DSL 把 deprecation 当编译错误，改脚本会直接失败。

## [0.1.0] - 2026-10-30

首个可用版本。目标多端，当前只实现 desktop（JVM）。

### 新增

- **块级适配器链**：`MdBlock` → `AdapterRegistry.select()` → `BlockAdapter.Render()`，
  按格式与优先级路由，最后一级是源码兜底，**永不留空白**。
- **解析器**：JetBrains GFM AST → 11 种块类型（标题、段落、代码块、引用、列表、表格、
  分割线、图片、HTML、公式、自定义），支持流式未闭合栅栏补齐。
- **内置适配器**：`builtin.markdown` / `builtin.code` / `builtin.mermaid` / `builtin.source`。
- **代码高亮**：Highlights + 少数格式的正则补丁；超长块跳过同步着色（`maxSyncHighlightLength`）。
- **图表**：`io.github.swithun-liu:mermaid-compose` 纯 Kotlin 引擎，不引 WebView；
  画不出的图族降级为源码 + 提示。
- **JCEF 回落**（`WebFallback.Jcef`）：OSR 离屏渲染，全程反射、零编译期依赖；
  运行期找不到 `org.cef` 自动退回源码。
- **浏览器实例池**：名额按**可见性**分配（到视口中心的距离优先），滚出范围即释放引擎，
  默认同时存活 2 个。
- **大块视口**：限高 + 双向滚动 + 固定档位缩放（`BlockViewportConfig`），
  横竖滚动条都可见。
- **主题**：`outsidePaperTheme()` 内置暖白纸面风格，明暗两套色板。
- **诊断**：`AdapterDiagnostics` 上报路由结果与渲染失败。

### 已知问题

- 关闭窗口后 JVM 可能不退出（CEF 全局实例的非 daemon 线程），宿主需自行兜底。
- `MdBlock.Table` 之外的复杂块（如超宽图片）依赖调用方给宽度，否则可能被压扁。
- 尚未内置 JCEF 的网络请求白名单，`<img src="http://...">` 这类追踪像素不会拦。
- Android / iOS / Web 只有结构预留，未实现。

### 修复（开发期踩到的坑，已在 0.1.0 内消化）

- 首个 `CefBrowser` 上「创建后再 `loadURL`」会丢 → 内容改走初始 URL。
- `CefRenderHandler.getScreenInfo` 未填 `CefScreenInfo` 时 `onPaint` 永不回调 → 已填充并返回 true。
- 视口把 `scaleLayout` 放在 `width(availableWidth)` 内层，导致横向滚动范围算成 0、横条不出现。
- 滚动条用 `fillMaxHeight()` 在无界约束下退化成 `Int.MAX_VALUE`，Skia 直接拒绝绘制。
- Setext 标题渲染成空、任务列表只显示 `[x]` 文本、长文档 JCEF 空白等块结构问题。
