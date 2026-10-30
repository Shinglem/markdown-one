# API 参考

包名统一 `io.github.shinglem.markdown`。所有 `public` 声明都显式写了修饰符（Kotlin 2.x explicit API 风格）。

## MarkdownText

唯一渲染入口。

```kotlin
@Composable
public fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
    config: MarkdownConfig = MarkdownConfig(),
)
```

| 参数 | 说明 |
|---|---|
| `markdown` | Markdown 源码。**直接传原文**，不要预先转义或做 WYSIWYG 转换 |
| `modifier` | 作用在最外层 `Box` 上 |
| `streaming` | 流式输出中：图表先占位、未闭合栅栏会被补齐、`kind = Jcef` 的适配器被跳过 |
| `config` | 见下表。`remember(config)` 会重建 registry，所以**不要每次重组都 new 一个** |

内部会自己铺整套 mikepenz `CompositionLocal`（不使用它的 `Markdown()` 入口），
所以宿主不需要额外配置 markdown renderer。

## MarkdownConfig

```kotlin
public data class MarkdownConfig(
    public val adapters: List<BlockAdapter> = emptyList(),
    public val disabledAdapterIds: Set<String> = emptySet(),
    public val jcefDuringStreaming: Boolean = false,
    public val allowRawHtml: Boolean = false,
    public val webFallback: WebFallback = WebFallback.Source,
    public val webFallbackHeight: Dp = 320.dp,
    public val webDocument: ((MdBlock) -> String?)? = null,
    public val theme: MarkdownTheme? = null,
    public val viewport: BlockViewportConfig = BlockViewportConfig(),
    public val jcefPoolSize: Int = 2,
    public val mermaidFallbackText: String = "图表语法暂不支持，已显示源码",
    public val maxSyncHighlightLength: Int = 8_000,
    public val onLinkClick: (String) -> Unit = {},
    public val diagnostics: AdapterDiagnostics = AdapterDiagnostics.None,
)
```

| 字段 | 默认 | 说明 |
|---|---|---|
| `adapters` | 空 | 显式注册的适配器，优先于内置与自动发现 |
| `disabledAdapterIds` | 空 | 按 id 禁用（对**所有**来源生效） |
| `jcefDuringStreaming` | false | 流式期间是否允许 JCEF 参与。默认关，避免每帧重载 Chromium |
| `allowRawHtml` | false | 是否渲染原始 HTML 块（否则只显示源码） |
| `webFallback` | `Source` | 原生画不出时的回落策略：`Source` / `Jcef` |
| `webFallbackHeight` | 320.dp | 回落渲染的块高（浏览器内容在内部滚动） |
| `webDocument` | null | `(MdBlock) -> String?`，为块生成 HTML；返回 null 表示不走回落 |
| `theme` | null | 外观；null 表示沿用 Material 3 默认 |
| `viewport` | `BlockViewportConfig()` | 大块限高、滚动、缩放 |
| `jcefPoolSize` | 2 | 同时存活的浏览器实例上限 |
| `mermaidFallbackText` | 内置文案 | 图表画不出时的提示 |
| `maxSyncHighlightLength` | 8000 | 超过这个长度的代码块跳过同步着色（避免卡 UI） |
| `onLinkClick` | 空实现 | 链接点击回调；不设则不可点击 |
| `diagnostics` | `None` | 路由与失败诊断 |

## WebFallback

```kotlin
public sealed interface WebFallback {
    public data object Source : WebFallback   // 直接显示源码，零依赖
    public data object Jcef : WebFallback     // 内嵌浏览器（运行期反射，找不到自动退回 Source）
}
```

## BlockViewportConfig

大块（图表、原始 HTML、宽表格、图片）的显示策略。

```kotlin
public data class BlockViewportConfig(
    public val enabled: Boolean = true,
    public val maxHeight: Dp = 420.dp,
    public val zoomable: Boolean = true,
    public val initialZoom: Float = 1f,
    public val zoomLevels: List<Float> = listOf(
        0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f, 1.1f, 1.25f, 1.5f, 2.0f, 2.5f,
    ),
    public val formats: Set<String> = setOf("mermaid", "html", "table", "image.generic"),
)
```

| 字段 | 说明 |
|---|---|
| `enabled` | 总开关 |
| `maxHeight` | 超过就出现纵向滚动条；`Dp.Unspecified` 表示不限高 |
| `zoomable` | 是否显示右上角的 `− 100% ＋` 缩放条 |
| `initialZoom` | 每块的初始缩放；`onReset` 仍回到 100% |
| `zoomLevels` | 固定档位 —— 连续 `±0.1` 会累积浮点漂移（100% 变 78%） |
| `formats` | 生效的格式前缀，按 `FormatId.value` 匹配 |

**不要往 `formats` 里加 `code`**：代码块自带横向滚动，再包一层会抛
`Horizontally scrollable component was measured with an infinity maximum width`。

表格在 150% 缩放下的样子（限高 + 底部横向滚动条 + 右上角缩放条）：

![视口](images/demo-viewport.png)

## MarkdownTheme

```kotlin
public data class MarkdownTheme(
    public val colors: MarkdownColors,        // mikepenz 的颜色表
    public val typography: MarkdownTypography,
    public val dimens: MarkdownDimens,
    public val padding: MarkdownPadding,
    public val background: Color = Color.Unspecified,
)

public fun outsidePaperTheme(dark: Boolean = false): MarkdownTheme
```

内置主题取自 typora `outside-theme` 的 **Outside Paper** 配色：暖白纸面 `#FDFCF9`、
正文 `#5A524A`、砖红强调 `#A8433A`、米黄代码块 `#F6F0E4`、行内码 `#F7E7DF`，行高统一 1.6。

想自己拼配色就直接取色板：

```kotlin
public data class PaperPalette(
    public val background: Color, public val surface: Color,
    public val text: Color, public val textMuted: Color,
    public val accent: Color, public val accentSoft: Color, public val accentWarm: Color,
    public val inlineCodeBackground: Color, public val divider: Color, public val quoteBackground: Color,
)

public val OutsidePaper: PaperPalette        // 亮
public val OutsidePaperDark: PaperPalette    // 暗
```

## MdBlock

```kotlin
public sealed interface MdBlock {
    public val key: String        // 块在文档里的标识（b0、b12…），跨文档会重复
    public val source: String     // 整篇源码：GFM AST 的 offset 是文档级的
    public val raw: String        // 该块的源码片段
    public val formats: Set<FormatId>
    public val node: ASTNode      // 原始 GFM 节点
}
```

| 类型 | 额外字段 | formats |
|---|---|---|
| `Heading` | `level: Int` | `markdown` |
| `Paragraph` | — | `markdown` |
| `CodeBlock` | `code: String`、`language: String?`、`fenced: Boolean` | `code:<lang>`，语言为 mermaid/mmd 时额外 `mermaid` |
| `Quote` | — | `markdown` |
| `ListBlock` | `ordered: Boolean` | `markdown` |
| `Table` | — | `markdown` + `table` |
| `Rule` | — | `markdown` |
| `Image` | `url: String`、`alt: String` | `image.generic` |
| `Html` | — | `html` |
| `Math` | `tex: String`、`display: Boolean` | `math` |
| `Custom` | `type: String` | 就是 `type` 本身 |

`MdDocument(blocks: List<MdBlock>, streaming: Boolean)` 是解析结果。

## FormatId / Formats

```kotlin
@JvmInline
public value class FormatId(public val value: String)   // 不能为空

public object Formats {
    public val Markdown: FormatId = FormatId("markdown")
    public val Mermaid: FormatId = FormatId("mermaid")
    public val Math: FormatId = FormatId("math")
    public val Html: FormatId = FormatId("html")
    public val Table: FormatId = FormatId("table")
    public val Image: FormatId = FormatId("image.generic")
    public val Any: FormatId = FormatId("*")
    public val Code: FormatId = FormatId("code:*")     // 前缀通配

    public fun code(language: String?): FormatId = FormatId("code:" + language?.trim()?.lowercase().orEmpty())
}
```

尾部 `*` 是前缀通配：`code:*` 命中 `code:kotlin`、`code:json`……

约定俗成的取值（不是枚举，可以自由扩展）：

| 值 | 含义 |
|---|---|
| `markdown` | 普通 Markdown 块（标题/段落/列表/表格/引用/分割线） |
| `code:<lang>` | 代码块，语言名小写；无语言时为 `code:` |
| `mermaid` | mermaid 图表（`code:mermaid` 的块同时声明它） |
| `math` | 数学公式 |
| `html` | 原始 HTML 块 |
| `table` | 表格（与 `markdown` 并存） |
| `image.generic` | 图片 |
| `*` | 通配，仅兜底适配器使用 |

## 适配器 SPI

```kotlin
public enum class AdapterKind { Native, Jcef, Fallback }

public interface BlockAdapter {
    public val id: String
    public val kind: AdapterKind
    public val priority: Int
    public fun formats(): Set<FormatId>
    public fun isAvailable(): Boolean
    public fun canRender(block: MdBlock): Boolean

    @Composable
    public fun Render(block: MdBlock, modifier: Modifier)
}

public fun interface BlockAdapterFactory {
    public fun create(): List<BlockAdapter>
}

public interface AdapterDiagnostics {
    public fun onRoute(blockKey: String, formats: Set<FormatId>, adapterId: String?, kind: AdapterKind?)
    public fun onFailure(adapterId: String, blockKey: String, error: Throwable?)
    public object None : AdapterDiagnostics
}
```

优先级约定：`Native` 100 / `Jcef` 50 / `Fallback` 0。详见 [写适配器](adapters.md)。

## AdapterRegistry

```kotlin
public class AdapterRegistry(adapters: List<BlockAdapter>) {
    public val all: List<BlockAdapter>
    public fun select(block: MdBlock, streaming: Boolean, allowJcef: Boolean = !streaming): BlockAdapter?
}

public fun buildAdapterRegistry(config: MarkdownConfig): AdapterRegistry
public fun builtinAdapters(config: MarkdownConfig): List<BlockAdapter>
```

`select` 返回 `null` 表示没有适配器接手（内置兜底在，正常不会发生）。
`builtinAdapters()` 可以拿来枚举内置能力，demo 的路由面板就是这么来的。

## 内置适配器

| id | kind | priority | formats |
|---|---|---|---|
| `builtin.markdown` | Native | 100 | `markdown` |
| `builtin.code` | Native | 100 | `code:*` |
| `builtin.mermaid` | Native | 100 | `mermaid`、`code:mermaid` |
| `builtin.jcef` | Jcef | 50 | `html`、`math`、`mermaid`（仅 desktop，且 `webFallback = Jcef` 时注册） |
| `builtin.source` | Fallback | 0 | `*` |

`builtin.jcef` 与 `builtin.mermaid` 在 `mermaid` 格式上重叠，但 mermaid 图默认**不会**进浏览器：
`builtin.mermaid`（priority 100）胜出，纯 Kotlin 引擎直接绘制；它画不出的图族会**自己**降级成
「源码 + `mermaidFallbackText` 提示」，不会路由到下一级。

想让 mermaid 改走浏览器，两步：

```kotlin
MarkdownConfig(
    webFallback = WebFallback.Jcef,
    disabledAdapterIds = setOf("builtin.mermaid"),   // 让出 mermaid 格式
    webDocument = { block -> if (block is MdBlock.CodeBlock) mermaidPage(block.code) else null },
)
```
