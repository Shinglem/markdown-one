# 架构

一次渲染从一段 Markdown 字符串开始，到屏幕上若干个独立渲染的块结束。

```
markdown: String
   │
   ├─ GfmMarkdownParser.parse(markdown, streaming)      ← org.jetbrains:markdown 的 GFM AST
   │      └─ MdDocument(blocks: List<MdBlock>, streaming)
   │
   ├─ 逐块 AdapterRegistry.select(block, streaming)     ← 按格式 + 优先级挑适配器
   │      └─ BlockAdapter.Render(block, modifier)
   │
   └─ Column { 每块 key(block.key) { [BlockViewport] { Render } } }
```

## 为什么按块拆，而不是整篇交给一个组件

把整篇 Markdown 交给一个渲染器是所有实现里最短的路，但撑不住这几种需求：

| 需求 | 整篇渲染的问题 |
|---|---|
| 图表、原始 HTML、公式需要完全不同的渲染手段 | 一个渲染器要么全支持、要么全不支持 |
| 流式输出要逐块稳定 | 每来一个字都重排整篇，图表反复重建 |
| 某一类内容的渲染必须能换实现 | 只能 fork 渲染器 |
| 某一类内容渲染失败不能拖垮整篇 | 一处异常整篇空白 |

拆成块之后，每种格式的渲染是**可替换的单元**，失败也只在那一块可见。

## 块模型

`MdBlock` 是 sealed interface，每个实现都带这五个公共字段：

| 字段 | 含义 | 注意 |
|---|---|---|
| `key` | 块在文档里的稳定标识（如 `b12`） | 跨文档会重复，只做 Compose `key()` 与诊断标签 |
| `source` | **整篇** Markdown 源码 | GFM AST 的 offset 是文档级的，渲染器会 `source.substring(start, end)`，传块片段会越界 |
| `raw` | 该块的源码片段 | 兜底展示、`LaunchedEffect` 的 key 成分 |
| `formats` | 该块能落到哪些格式（`Set<FormatId>`） | 路由唯一依据，一个块可以同时有多个格式 |
| `node` | 原始 GFM AST 节点 | 需要行内信息（链接、粗体）时用 |

具体类型：`Heading` / `Paragraph` / `CodeBlock` / `Quote` / `ListBlock` / `Table` / `Rule` /
`Image` / `Html` / `Math` / `Custom`。

格式映射（`formats`）的几个要点：

- `CodeBlock` 恒有 `code:<language>`，语言为 `mermaid` / `mmd` 时**额外**带 `mermaid`；
- `Table` 同时带 `markdown` 与 `table` —— 前者让 markdown 适配器接管渲染，
  后者让 `BlockViewportConfig` 能单独给表格限高加滚动条；
- `Custom` 的格式就是它的 `type`，调用方可以自己定义块类型（见 [写适配器](adapters.md)）。

## 路由

`AdapterRegistry.select()` 是一条流水线，顺序不能乱：

```
all                                // 显式注册 → ServiceLoader 发现 → 内置，按 id 去重（先到者胜）
  .filter { kind != Jcef || allowJcef }   // 流式期间 JCEF 不参与
  .filter { matches(block) }              // 声明的格式与 block.formats 有交集
  .filter { isAvailable() }               // 运行期探测（反射、可选依赖）
  .filter { canRender(block) }            // 比格式更细的判定
  .maxByOrNull { priority }               // 取最高优先级；同优先级时先注册者胜
```

`matches` 的三种命中方式：

1. `Formats.Any`（`*`）—— 兜底适配器用，命中一切；
2. 精确相等 —— 格式值相同；
3. **尾部通配**：声明值以 `*` 结尾时按前缀匹配，所以 `code:*` 命中 `code:kotlin`。

返回 `null` 表示没有适配器接手，此时该块**不渲染任何东西** —— 所以内置的
`builtin.source`（`Formats.Any`，priority 0）永远排在最后，保证不出现空白。

## 两条纪律

**禁止静默替换。** 适配器画不出这个块时，必须让路由落到下一级，不能自己换个引擎顶替。
否则用户看到的画面与诊断面板对不上，线上排查会完全失去线索。

**流式只降不升。** `streaming = true` 时 `AdapterKind.Jcef` 的适配器被整个跳过 ——
流式每几十毫秒一次重组，每次重载 Chromium 会让画面持续闪烁并吃满 CPU。
流式结束后自动回到正常路由，不需要调用方做任何事。

## 布局约束

- 块之间是普通 `Column`，**不是 LazyColumn**：本组件通常嵌在宿主的 LazyColumn 里，
  再嵌一层会在无界高度约束下崩（`Nesting scrollable in the same direction` / 测量溢出）。
- 每个块用 `key(block.key)` 包裹，保证块的身份在重组间稳定。
- 大块限高与缩放由 `BlockViewport` 在外面再包一层，见 [API · BlockViewportConfig](api.md#blockviewportconfig)。

## 平台分层

```
commonMain/                      解析、块模型、适配器 SPI、内置 Native 适配器、主题、视口
 ├── adapter/BlockAdapter.kt     SPI
 ├── parse/                      JetBrains GFM AST → MdBlock
 ├── builtin/                    markdown / code / mermaid / source
 └── MarkdownText.kt             唯一入口

desktopMain/                     只有平台差异
 ├── adapter/AdapterDiscovery    ServiceLoader 发现（其它端返回空列表）
 ├── adapter/PlatformAdapters    注册 JCEF 回落适配器
 ├── BlockScrollbarOverlay       原生滚动条
 └── web/                        CefBootstrap / CefEngine / CefSlot / JcefBudget / JcefAdapter
```

`commonMain` 里唯一与平台有关的两个函数是 `expect` 的：

```kotlin
internal expect fun discoverAdapterFactories(): List<BlockAdapterFactory>
internal expect fun platformAdapters(config: MarkdownConfig): List<BlockAdapter>
```

加新端时只需要给这两个写 `actual`：Android 用反射扫 dex 或直接返回空列表（靠显式注册），
iOS/Web 返回空列表。JCEF 适配器在 `desktopMain` 且全程反射，其它端根本看不到 `org.cef`。

## 线程与生命周期

| 位置 | 线程 | 说明 |
|---|---|---|
| 解析 | Compose 主线程 | 纯 Kotlin，长文档也在毫秒级；`remember(markdown, streaming)` 缓存 |
| 内置适配器渲染 | Compose 主线程 | 只做排版与绘制 |
| JCEF 引擎创建 | 独立线程（`markdown-one-cef`） | 创建要 1–2 秒，放在 `LaunchedEffect` 里会被重组取消，见 [JCEF](jcef.md) |
| JCEF 出帧 | CEF 线程 → `SwingUtilities.invokeLater` | 帧回调切回 Compose 线程再写 state |
| JCEF 释放 | 独立线程（`markdown-one-cef-dispose`） | `close`/`dispose` 会等 CEF UI 线程，主线程调会卡死窗口 |

## 相关文档

- [写适配器](adapters.md) —— SPI 每个方法的语义与陷阱
- [JCEF 回落](jcef.md) —— 内嵌浏览器的完整接入与已知坑
- [API 参考](api.md) —— 所有公开类型与字段
