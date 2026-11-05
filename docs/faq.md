# 常见问题

## 某块显示的是源码，不是渲染结果

按可能性从高到低：

1. **这个格式本来就没有原生适配器**。原始 HTML 白名单外的标签、冷门图族、KaTeX 扩展包语法
   （`\ce{}` / `\SI{}`）都在这一类，会显示源码。
2. **适配器 `isAvailable()` 返回 false**，例如 native 公式库没加载成功。
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

`builtin.diagram`（`diagram-render`）覆盖 Mermaid / PlantUML / DOT 的常见图族，
冷门语法会显示源码 + 一行提示 —— 这是刻意的降级，不是 bug。

## 公式不显示，只看到 TeX 源码

1. **语法不在 KaTeX 集合里**。`ratex` 只按 KaTeX 语法集排版；`mhchem`（`\ce{}`）、
   `siunitx`（`\SI{}`）属于扩展包，默认都不支持（GitHub 也不支持）。
2. **native 库没加载**。`ratex` 的 dll 由 `io.github.darriousliu:ratex-native-windows-x86-64`
   提供，必须是 `runtimeOnly` 依赖且在 classpath 上；缺了会退化成源码 + `mathFallbackText`。
3. **公式引擎只在 desktop 注册**，其它 target 一律显示源码。

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

## 流式输出时图表一直在「生成中」

流式期间图表适配器只显示占位，等 `streaming = false` 再真正排版。
这是刻意的 —— 每帧全量重排会让滚动持续抖动。

如果连占位都不想看到，把 `builtin.diagram` 加进 `disabledAdapterIds`，流式期间就只显示源码。

## HTML 块里的样式不生效

只认 6 个能映射到 `TextStyle` 的属性：`color` / `background-color` / `font-size` /
`font-weight` / `font-style` / `text-align`。`display` / `flex` / `grid` / `position` /
`float` / `margin` / `padding` 一律**静默忽略** —— 渲染目标是「对话里的富文本」，
不是「用 HTML 写页面」（依据见 GitHub 自己的 sanitizer：它连 `style` 都不允许）。

## 脚注写 `[^1]` 却原样显示

- 脚注是 GitHub 的**产品特性**，不是 CommonMark / GFM 规范，所以本库在解析前预处理：
  `[^id]: 内容` 必须独占一行（可缩进续行），正文里的 `[^1]` 才会变成上标序号；
- 引用了但没有对应定义时保持字面量（与 GitHub 一致）；
- 代码块与行内代码里的 `[^1]` 不动。

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
- 公式适配器（`ratex` + native dll）只在 `desktopMain`。

需要加新端时的步骤见 [CONTRIBUTING · 加一个 target](../CONTRIBUTING.md#加一个-target)。

## 可以商用吗

可以，[Apache-2.0](../LICENSE)。注意图表引擎、公式引擎、HTML 解析器、代码高亮、Coil
等依赖各自的许可证，见 [README · 依赖](../README.md#依赖)。
