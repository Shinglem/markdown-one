# 常见问题

## 某块显示的是源码，不是渲染结果

按可能性从高到低：

1. **这个格式本来就没有原生适配器**。数学公式没有内置渲染器（要 KaTeX 得自己注入，
   见 [JCEF · 开关三件套](jcef.md#开关三件套)）；原始 HTML 默认关（`allowRawHtml = false`）。
2. **适配器 `isAvailable()` 返回 false**。比如 `webFallback = Jcef` 但运行环境不是 JBR with JCEF。
3. **适配器的 `canRender()` 拒了它**。加 `AdapterDiagnostics` 看 `onRoute` 的 `adapterId`：

   ```kotlin
   MarkdownConfig(diagnostics = object : AdapterDiagnostics {
       override fun onRoute(blockKey: String, formats: Set<FormatId>, adapterId: String?, kind: AdapterKind?) {
           println("$blockKey $formats → $adapterId")
       }
       override fun onFailure(adapterId: String, blockKey: String, error: Throwable?) {}
   })
   ```

源码兜底是设计的一部分：**永远不留空白**，画不出就一定看得到原文。

## 图表画不出来 / 只显示「图表语法暂不支持」

`builtin.mermaid` 是纯 Kotlin 引擎，只支持一部分图族（flowchart、sequence 等）。
不支持的语法会显示源码 + 一行提示 —— 这是刻意的降级，不是 bug。

想要完整 mermaid 语法树，就切到浏览器渲染（[API · 内置适配器](api.md#内置适配器) 末尾）。

## JCEF 块占了高度但一直空白

按这个顺序查：

1. **JBR with JCEF 吗？** 普通 JDK 没有 `jcef` 模块。`JcefRuntime.isPresent` 为 false 时
   会静默退回源码，看到的是源码而不是空白。
2. **启动参数齐全吗？** `--add-modules jcef`、`--enable-native-access=jcef`、两个 `--add-opens`。
3. **`getScreenInfo` 填了吗？** 返回 false 时 CEF 认为没有可用屏幕，`onPaint` 一帧都不来，
   日志里也没有任何报错。这是最隐蔽的一个，见 [JCEF · 六个静默失败的坑](jcef.md#六个静默失败的坑)。
4. **是不是文档里第一个浏览器？** 首个 `CefBrowser` 上「创建后再 `loadURL`」会丢，
   内容必须走初始 URL —— 库已修，但如果你 fork 过 `CefEngine`，检查这里。

## 第二个及以后的块显示「等待浏览器实例」

这是名额机制在工作，不是错误。`jcefPoolSize`（默认 2）限制同时存活的浏览器数，
没拿到的块显示占位，滚动到它附近会自动接手。

觉得太少就调大 —— 但每个实例是一个完整的 Chromium 渲染进程（5+ 子进程），
一块一个会把内存吃光。

## 想彻底关掉 JCEF

```kotlin
MarkdownConfig(webFallback = WebFallback.Source)   // 默认值，只是显式写清楚
```

这样连 `org.cef` 都不会被探测，块直接显示源码。普通 JDK 上也是这个行为。

## 滚动条看不到

- **块没有边框**时，10dp 的浅色滚动条在深色背景上容易看不见；
- 只有内容**真的超出**时才出现：横向要求缩放后宽度 > 视口宽，纵向要求内容高 > `maxHeight`；
- 极端情况下如果 `contentWidth` 停在原尺寸，横向滚动范围会算成 0 —— 这是曾经踩过的实现 bug
  （`scaleLayout` 必须在 `width(availableWidth)` 外层），升级到最新版即可。

## 表格没有限高 / 不能滚动

`BlockViewportConfig.formats` 默认包含 `table`，前提是块被解析成 `MdBlock.Table`。
表格在块模型里同时带 `markdown` 与 `table` 两个格式，所以渲染仍由 `builtin.markdown` 完成，
视口只是多包了一层。

如果你的表格落在 `Paragraph` 里（少见，通常意味着表格语法不完整），它就不会进视口。

## 报错 `Horizontally scrollable component was measured with an infinity maximum width`

滚动容器嵌套了。最常见的原因是把 `code` 加进了 `BlockViewportConfig.formats` ——
代码块自带横向滚动。从 `formats` 里去掉即可。

## 关掉窗口后进程不退出

已知现象：demo 用 JCEF 时，窗口关闭后 JVM 可能还活着（`exitApplication` 已执行，
但有非 daemon 线程未结束，多半来自 CEF 的全局 `CefApp`）。

workaround：宿主在窗口关闭流程里显式兜底退出。库本身不强制 `exitProcess`，
避免把宿主的其他窗口一起杀掉。这条在 [路线图](roadmap.md#2-进程退出兜底) 里。

## 流式输出时图表一直在「生成中」

流式期间 `kind = Jcef` 的适配器被跳过，mermaid 会先显示占位，等 `streaming = false` 再绘制。
这是刻意的 —— 每帧重载渲染引擎会让画面持续闪烁。

如果连占位都不想看到，把 `mermaid` 也加进 `disabledAdapterIds`，流式期间就只显示源码。

## 超长代码块卡顿

`maxSyncHighlightLength`（默认 8000）以上的代码块跳过同步着色。
调小它可以让超大块更早放弃着色：

```kotlin
MarkdownConfig(maxSyncHighlightLength = 4_000)
```

## 中文、emoji、混排有问题吗

没问题。解析用 JetBrains 的 GFM 实现，渲染用 Compose 原生文本，中文与 emoji
在「复杂综合文档」fixture 里都有覆盖（包含 Setext 标题、任务列表、嵌套列表）。

## 能在 Android / iOS / Web 上用吗

结构上可以，当前只实现了 desktop：

- 全部逻辑在 `commonMain`；
- 平台差异只有 `discoverAdapterFactories()` 与 `platformAdapters()` 两个 `expect` 函数；
- JCEF 适配器在 `desktopMain`，其它端看不见。

需要加新端时的步骤见 [CONTRIBUTING · 加一个 target](../CONTRIBUTING.md#加一个-target)。

## 可以商用吗

可以，[Apache-2.0](../LICENSE)。注意 mermaid 引擎、mikepenz renderer 等依赖各自的许可证，
见 [README · 依赖](../README.md#依赖)。
