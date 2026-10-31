package io.github.shinglem.markdown.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.shinglem.markdown.BlockViewportConfig
import io.github.shinglem.markdown.MarkdownConfig
import io.github.shinglem.markdown.MarkdownText
import io.github.shinglem.markdown.OutsidePaper
import io.github.shinglem.markdown.OutsidePaperDark
import io.github.shinglem.markdown.WebFallback
import io.github.shinglem.markdown.outsidePaperTheme
import io.github.shinglem.markdown.adapter.AdapterDiagnostics
import io.github.shinglem.markdown.adapter.AdapterKind
import io.github.shinglem.markdown.adapter.FormatId

private data class Fixture(val name: String, val markdown: String, val streaming: Boolean = false)

private val FIXTURES = listOf(
    Fixture(
        "复杂综合文档",
        """
        # 复杂文档渲染验证

        > 这个 fixture 尽量覆盖每一种块类型，用来目视检查适配器链的整体效果。
        > 右侧面板会列出每个块命中的适配器。

        ## 一、文本与行内格式

        普通段落：**粗体**、*斜体*、~~删除线~~、`行内代码`、[外部链接](https://example.com)，
        以及中文与 English 混排，再加个 emoji ✅ 🚀。

        ### 1.1 三级小节

        Setext 标题
        -----------

        上面这行是 setext 写法的二级标题。

        #### 1.1.1 四级标题

        ##### 五级标题

        ###### 六级标题

        ## 二、列表

        - 无序项一
        - 无序项二
          - 嵌套层级 A
          - 嵌套层级 B
            - 更深一层
        - 无序项三

        1. 有序一
        2. 有序二
           1. 嵌套有序
           2. 嵌套有序

        - [x] 已完成的任务
        - [ ] 未完成的任务

        ## 三、表格

        | 语言 | 场景 | 体积 | 备注 |
        | :--- | :--- | ---: | --- |
        | Kotlin | 主力 | 小 | **推荐** |
        | Rust | 高性能 | 中 | 需要 native |
        | `JS` | 生态 | 大 | 不引入 |

        ## 四、代码

        ```kotlin
        data class Adapter(
            val id: String,
            val priority: Int,
        ) {
            fun formats(): Set<String> = setOf("markdown", "code")
        }
        ```

        ```json
        {
          "adapters": [
            { "id": "builtin.jcef", "priority": 50, "kind": "Jcef" }
          ],
          "enabled": true
        }
        ```

        ```bash
        ./gradlew :desktopTest
        git -c http.proxy=http://127.0.0.1:7890 push
        ```

        ```diff
        - 旧实现：SwingPanel + heavyweight Canvas
        + 新实现：OSR + BGRA → ImageBitmap
        ```

        ## 五、图表

        ```mermaid
        flowchart TD
          A[输入 Markdown] --> B{解析}
          B --> C[块序列]
          C --> D{适配器路由}
          D -->|Native| E[Compose 绘制]
          D -->|Jcef| F[浏览器离屏]
          D -->|都不行| G[源码兜底]
        ```

        ```mermaid
        sequenceDiagram
          participant U as 用户
          participant C as 客户端
          participant B as 后端
          U->>C: prompt.submit
          C->>B: JSON-RPC
          B-->>C: event 流
          C-->>U: 增量渲染
        ```

        ## 六、公式

        $$
        f(x) = \int_{-\infty}^{\infty} \hat{f}(\xi) e^{2\pi i \xi x} d\xi
        $$

        ## 七、原始 HTML

        <div align="center">
          <b>这段由 JCEF 渲染</b>；把 webFallback 改成 Source 就会显示源码。
        </div>

        ## 八、引用与分割线

        > 一层引用
        >
        > > 嵌套引用

        ---

        ## 九、长段落

        这是一段较长的中文，用来检查换行、行距与段落间距是否符合预期。渲染层把整篇文档解析成块序列，
        再逐块交给适配器；每一块独立渲染，因此单块失败不会影响其它块。长段落在窄窗口下应当正常折行，
        不应该出现横向滚动条，也不应该把字符裁掉。中文、English、数字 123 与符号 @#% 混排时，
        断行位置应当由文本引擎决定，而不是按固定字宽估算。
        """.trimIndent(),
    ),
    // 公式 fixture 用 `§` 代替 `$`，避免 Kotlin raw string 里的模板插值（替换在 trimIndent 之后做）。
    //
    // 公式由 `io.github.huarangmeng:latex-renderer` 排版（Compose 矢量绘制 + KaTeX 字体），
    // 大写希腊字母（含 `\Omega`）与 `\begin{pmatrix}` 都正常。
    Fixture(
        "公式",
        """
        # 公式渲染

        ## 一、行内公式（`§…§`，`§` 前留空格才会识别）

        爱因斯坦质能方程 §E = mc^2§，欧拉恒等式 §e^{i\pi} + 1 = 0§。

        分式 §\frac{a+b}{c}§、根号 §\sqrt{x^2 + y^2}§、求和 §\sum_{i=1}^{n} i = \frac{n(n+1)}{2}§。

        希腊字母 §\alpha, \beta, \gamma, \Delta, \Omega, \Psi§；关系符 §\leq, \geq, \neq, \approx§。

        **粗体里的公式 §\int_0^1 x^2\,dx = \frac{1}{3}§ 也应正常。**

        ## 二、紧贴标点（按源码显示）

        当 §n \to \infty§ 时，§\bar{x} \to \mu§。

        上面第二个公式紧贴中文逗号，按 0.7.17 的 `canOpenMath` 规则不渲染，
        保留成源码（与上游一致，刻意不补偿）。

        ## 三、行内代码里的美元符

        价格写作 `§100` 与 `§200`，不应变成公式。

        ## 四、块级公式（`§§…§§`）

        §§
        f(x) = \int_{-\infty}^{\infty} \hat{f}(\xi)\, e^{2\pi i \xi x}\, d\xi
        §§

        §§
        \begin{pmatrix} a & b \\ c & d \end{pmatrix}
        \begin{pmatrix} x \\ y \end{pmatrix}
        =
        \begin{pmatrix} ax + by \\ cx + dy \end{pmatrix}
        §§

        §§
        \sum_{k=1}^{n} k^2 = \frac{n(n+1)(2n+1)}{6}
        §§

        ## 五、嵌套在引用与列表里

        > 引用里的行内公式 §a^2 + b^2 = c^2§ 与块级公式：
        >
        > §§
        > \int_0^{\pi} \sin x\, dx = 2
        > §§

        - 列表项里的块级公式：

          §§
          e^{i\pi} = -1
          §§

        - 列表项里的 mermaid：

          ```mermaid
          flowchart LR
            A[开始] --> B[结束]
          ```

        ## 六、语法不支持时

        §§
        \undefinedcommand{x}
        §§
        """.trimIndent().replace('§', '$'),
    ),
    Fixture(
        "基础语法",
        """
        # 标题一
        ## 标题二

        一段普通文本，含 **粗体**、*斜体*、`行内代码` 与 [链接](https://example.com)。

        - 列表项一
        - 列表项二
          - 嵌套项

        1. 有序一
        2. 有序二

        > 引用块

        ---
        """.trimIndent(),
    ),
    Fixture(
        "代码块",
        """
        ```kotlin
        fun main() {
            println("hello")
        }
        ```

        ```json
        { "a": 1, "b": [true, null] }
        ```

        ```sql
        select id, name from users where id = 1;
        ```

        ```未知语言
        这段没有高亮规则
        ```
        """.trimIndent(),
    ),
    Fixture(
        "表格",
        """
        | 列 A | 列 B | 列 C |
        | --- | --- | --- |
        | 1 | 2 | 3 |
        | 中文 | `code` | **粗** |
        """.trimIndent(),
    ),
    Fixture(
        "图表",
        """
        ```mermaid
        flowchart LR
          A[开始] --> B{判断}
          B -->|是| C[结束]
          B -->|否| A
        ```
        """.trimIndent(),
    ),
    Fixture(
        "流式未闭合",
        """
        正在回答中……

        ```kotlin
        val x = 1
        """.trimIndent(),
        streaming = true,
    ),
    Fixture(
        "多个 HTML 块（池上限 2）",
        """
        # 浏览器实例池

        下面有 4 个 HTML 块，但池上限是 2 —— 滚动时应看到「等待浏览器实例」的占位，
        滚回上方时重新分配。每个块一个 Chromium 进程会把内存吃光。

        <div align="center" style="font-size:28px">① 第一块</div>

        中间隔一段文字，把块拉开距离。

        <div align="center" style="font-size:28px">② 第二块</div>

        再隔一段文字。

        <div align="center" style="font-size:28px">③ 第三块</div>

        最后一段。

        <div align="center" style="font-size:28px">④ 第四块</div>
        """.trimIndent(),
    ),
    Fixture(
        "原始 HTML 与公式",
        """
        <div align="center">一段原始 HTML</div>

        $$
        E = mc^2
        $$
        """.trimIndent(),
    ),
)

/**
 * sandbox：左 fixture、中渲染、右**适配器路由面板**。
 *
 * 路由面板是这套架构最重要的自检工具 —— 每个块命中了哪个适配器、什么优先级、什么类型，
 * 一眼可见；改优先级或禁用一个适配器，结果立刻反映出来。
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "markdown-one demo",
        state = rememberWindowState(width = 1500.dp, height = 950.dp),
    ) {
        DemoScreen()
    }
}

@Composable
private fun DemoScreen() {
    val initial = remember {
        // -Ddemo.fixture（或环境变量 DEMO_FIXTURE）支持名称或序号：
        // 序号更安全，避免命令行中文编码问题。
        val wanted = System.getProperty("demo.fixture") ?: System.getenv("DEMO_FIXTURE")
        FIXTURES.firstOrNull { it.name == wanted }
            ?: wanted?.toIntOrNull()?.let { FIXTURES.getOrNull(it) }
            ?: FIXTURES.first()
    }
    val selected = remember { mutableStateOf(initial) }
    val routes = remember { mutableStateMapOf<String, String>() }
    // 按 fixture 名分组：切 fixture 时不清空面板，靠前缀区分，避免「清了却不重填」。
    val currentName = remember { mutableStateOf(initial.name) }
    var darkPaper by remember { mutableStateOf(false) }
    val paper = if (darkPaper) OutsidePaperDark else OutsidePaper

    val theme = outsidePaperTheme(dark = darkPaper)

    // -Ddemo.zoom=1.5（或 DEMO_ZOOM）把每块视口的初始缩放拉起来，用来验证横向滚动条。
    val initialZoom = remember {
        (System.getProperty("demo.zoom") ?: System.getenv("DEMO_ZOOM"))?.toFloatOrNull() ?: 1f
    }
    // -Ddemo.scroll=520（或 DEMO_SCROLL）把正文滚到指定位置，方便截图核对屏幕外的大块。
    val contentScroll = remember {
        (System.getProperty("demo.scroll") ?: System.getenv("DEMO_SCROLL"))?.toIntOrNull() ?: 0
    }

    val config = remember(theme, initialZoom) {
        MarkdownConfig(
            // 开关演示：原始 HTML 块交给 JCEF 渲染；运行环境没有 org.cef 时自动退回源码。
            webFallback = WebFallback.Jcef,
            allowRawHtml = true,
            theme = theme,
            viewport = BlockViewportConfig(initialZoom = initialZoom),
            diagnostics = object : AdapterDiagnostics {
                override fun onRoute(blockKey: String, formats: Set<FormatId>, adapterId: String?, kind: AdapterKind?) {
                    val value = "${formats.joinToString("/")} → ${adapterId ?: "无"} (${kind ?: "-"})"
                    val key = "${currentName.value}/$blockKey"
                    if (routes[key] != value) routes[key] = value
                }

                override fun onFailure(adapterId: String, blockKey: String, error: Throwable?) {
                    routes["${currentName.value}/$blockKey"] = "失败: $adapterId ${error?.message.orEmpty()}"
                }
            },
        )
    }

    Row(Modifier.fillMaxSize().background(paper.background)) {
        Column(Modifier.width(200.dp).fillMaxHeight().background(paper.surface)) {
            FIXTURES.forEach { fixture ->
                val active = fixture == selected.value
                Text(
                    text = fixture.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (active) paper.accent else paper.text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (active) paper.inlineCodeBackground else paper.surface)
                        .clickable {
                            currentName.value = fixture.name
                            selected.value = fixture
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
            Box(Modifier.weight(1f))
            Text(
                text = if (darkPaper) "纸面 · 暗" else "纸面 · 亮",
                style = MaterialTheme.typography.labelSmall,
                color = paper.textMuted,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { darkPaper = !darkPaper }
                    .padding(12.dp),
            )
        }

        VerticalDivider(color = paper.divider.copy(alpha = 0.3f))

        Column(
            Modifier.weight(1f).fillMaxHeight()
                .verticalScroll(rememberScrollState(initial = contentScroll))
                .padding(24.dp),
        ) {
            MarkdownText(
                markdown = selected.value.markdown,
                streaming = selected.value.streaming,
                config = config,
            )
        }

        VerticalDivider(color = paper.divider.copy(alpha = 0.3f))

        Column(Modifier.width(360.dp).fillMaxHeight().padding(16.dp)) {
            Text("适配器路由", style = MaterialTheme.typography.titleSmall, color = paper.text)
            Text(
                text = "块 → 命中的适配器 (kind)",
                style = MaterialTheme.typography.labelSmall,
                color = paper.textMuted,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LazyColumn {
                items(routes.toList().filter { it.first.startsWith("${currentName.value}/") }) { (key, value) ->
                    Text(
                        text = "${key.substringAfter('/')}  $value",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = paper.text,
                        modifier = Modifier.padding(vertical = 3.dp),
                    )
                }
            }
        }
    }
}
