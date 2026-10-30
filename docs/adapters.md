# 写一个适配器

库内置的适配器只覆盖「纯 Compose 能画」的内容。任何需要平台能力的东西
（数学公式排版、内嵌浏览器、原生控件、画布绘图）都由调用方自己实现 `BlockAdapter`。

## SPI

```kotlin
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
```

| 成员 | 语义 | 常见错误 |
|---|---|---|
| `id` | 唯一标识，用于覆盖与禁用 | 和内置 id 冲突会**覆盖**内置实现 |
| `kind` | `Native` / `Jcef` / `Fallback` | `Jcef` 会在流式期间被整体跳过 |
| `priority` | 越大越优先；同值先注册者胜 | 想接管内置实现就 > 100 |
| `formats()` | 支持的格式集合 | 每次渲染都会调用，**别在里面做重活** |
| `isAvailable()` | 运行期探测（类是否存在、初始化是否成功） | 必须缓存结果，`select()` 每块都问一次 |
| `canRender()` | 比格式更细的判定（看元数据、看内容） | 返回 false 表示「这个块我确实画不了」 |
| `Render()` | 真正绘制 | 画不出来必须**自己**显示源码并留提示，兜底只在没适配器命中时生效 |

`modifier` 已经从外面带上了 `fillMaxWidth()`，直接用到根节点上即可。

`formats()` 的三种写法：

```kotlin
override fun formats() = setOf(Formats.Math)        // 精确
override fun formats() = setOf(Formats.code("kotlin")) // 只接管 kotlin 代码块
override fun formats() = setOf(Formats.Code)        // `code:*` 前缀通配，接管所有代码块
override fun formats() = setOf(Formats.Any)         // 命中一切（只该给兜底用）
```

## 注册

三种方式，按优先级从高到低：

### 1. 显式注册（最常用）

```kotlin
MarkdownText(
    markdown = text,
    config = MarkdownConfig(adapters = listOf(MyMathAdapter())),
)
```

`MarkdownConfig.adapters` 排在最前面，同 `id` 时覆盖内置或自动发现的实现。

### 2. ServiceLoader 自动发现（可选 artifact 用）

在自己的 artifact 里放一个实现，再登记一行：

```
src/main/resources/META-INF/services/io.github.shinglem.markdown.adapter.BlockAdapterFactory
```

内容是实现类的全限定名。classpath 上存在即生效（仅 JVM/desktop）。
适合把「重依赖适配器」（KaTeX、原生渲染器）拆成单独 artifact，调用方加依赖就自动启用。

### 3. 禁用内置

```kotlin
MarkdownConfig(disabledAdapterIds = setOf("builtin.mermaid"))
```

禁用按 `id` 过滤，**来源不限** —— 显式注册里同 id 的适配器也会被跳过。
所以「替换内置实现」要做的是**新起一个 id** 并把 `priority` 抬到内置之上，而不是复用内置 id。

## 完整示例

接管数学公式：优先用宿主的公式渲染器，画不出就显示 TeX 源码。

```kotlin
class MyMathAdapter : BlockAdapter {
    override val id = "my.math"
    override val kind = AdapterKind.Native
    override val priority = 120                      // 高于内置的 100，接管公式
    override fun formats() = setOf(Formats.Math)
    override fun isAvailable() = true
    override fun canRender(block: MdBlock) = block is MdBlock.Math

    @Composable
    override fun Render(block: MdBlock, modifier: Modifier) {
        val math = block as MdBlock.Math
        val image = remember(math.tex, math.display) { renderTexToImage(math.tex) }
        if (image == null) {
            // 画不出来：自己降级 + 留可见痕迹。不要静默吞掉。
            Text(
                text = "公式渲染不可用，显示源码：\n${math.tex}",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = modifier,
            )
            return
        }
        Image(bitmap = image, contentDescription = math.tex, modifier = modifier)
    }
}
```

### 带一点「只有部分块能画」的判定

```kotlin
override fun canRender(block: MdBlock): Boolean {
    val math = block as? MdBlock.Math ?: return false
    return math.tex.length <= 2_000      // 超长的交给下一级，避免排版卡住
}
```

### 只有特定语言才接管

```kotlin
override fun formats() = setOf(Formats.code("sql"))
override fun canRender(block: MdBlock) =
    (block as? MdBlock.CodeBlock)?.code?.lineSequence()?.count() ?: 0 < 500
```

## 自定义块类型

需要「Markdown 里没有、但你的场景里有」的块时，用 `MdBlock.Custom`：

```kotlin
override fun formats() = setOf(FormatId("chart.vega"))   // 自定义格式值
```

`Custom.formats` 就是它的 `type` 字段，所以解析侧产出的 `type` 与适配器声明对上即可。
解析侧要产出 `Custom` 需要换掉解析器（`GfmMarkdownParser` 是内置实现），
目前支持的扩展路径是「用 `webDocument` 把任意块交给浏览器」或「自己解析后调用 `builtinAdapters`」——
如果确实需要解析期扩展点，请提 issue。

## 视口（限高 + 滚动 + 缩放）

不需要在适配器里自己实现。只要让块的 `formats` 命中 `BlockViewportConfig.formats`
（默认 `mermaid` / `html` / `table` / `image.generic`），外面就会自动包一层视口。

对自定义格式：

```kotlin
MarkdownConfig(
    viewport = BlockViewportConfig(
        formats = setOf("mermaid", "html", "table", "image.generic", "chart.vega"),
    ),
)
```

**不要**把 `code` 加进去 —— 代码块自己带横向滚动，再包一层会抛
`Horizontally scrollable component was measured with an infinity maximum width`。

## 诊断

调试路由用 `AdapterDiagnostics`：

```kotlin
MarkdownConfig(
    diagnostics = object : AdapterDiagnostics {
        override fun onRoute(blockKey: String, formats: Set<FormatId>, adapterId: String?, kind: AdapterKind?) {
            println("$blockKey ${formats.joinToString("/")} → ${adapterId ?: "无"} ($kind)")
        }

        override fun onFailure(adapterId: String, blockKey: String, error: Throwable?) {
            println("$blockKey 渲染失败：$adapterId ${error?.message}")
        }
    },
)
```

`onRoute` 的 `adapterId` 为 `null` 说明没有适配器命中（正常情况下不该发生，兜底会接住）。
