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
            { "id": "builtin.diagram", "priority": 100, "kind": "Native" }
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
          <b>这段由原生 HTML 适配器渲染</b>（ksoup 清洗 → Compose 块树）。
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
    // 公式由 `io.github.darriousliu:ratex` 排版（Rust 核心 + Compose 矢量绘制），
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
        "HTML 原生渲染",
        """
        # HTML 块原生渲染

        下面几块都是**原始 HTML**，由 ksoup 清洗后交给 Compose 原生渲染：
        标签语义（层级、列表、引用、代码、表格）保留，布局级 CSS 一律忽略。

        <div align="center" style="font-size:28px">① 居中大字</div>

        <h3>标题与段落</h3>
        <p>这是一段 <b>粗体</b>、<i>斜体</i>、<code>行内码</code> 与
        <a href="https://example.com">链接</a> 混排的 HTML 段落。</p>

        <blockquote>HTML 里的引用块</blockquote>

        <ul>
          <li>无序一</li>
          <li>无序二
            <ol>
              <li>嵌套有序</li>
            </ol>
          </li>
        </ul>

        <pre><code>val html = "&lt;div&gt;"
        println(html)</code></pre>

        <table border="1">
          <tr><th>列 A</th><th>列 B</th></tr>
          <tr><td>1</td><td>2</td></tr>
        </table>

        <div>前<script>alert('不应执行')</script>后（脚本子树被整棵丢弃）</div>
        """.trimIndent(),
    ),
    Fixture(
        "Alerts 与脚注",
        """
        # Alerts 与脚注

        > [!NOTE]
        > 有用信息：这一段是 NOTE。

        > [!TIP]
        > 小技巧：这一段是 TIP。

        > [!IMPORTANT]
        > 关键：这一段是 IMPORTANT。

        > [!WARNING]
        > 注意：这一段是 WARNING。

        > [!CAUTION]
        > 危险：这一段是 CAUTION。

        正文引用一条脚注[^1]，再引用一条带格式的[^2]。

        [^1]: 第一条脚注的内容。
        [^2]: 第二条脚注，里面有 `行内码` 与**粗体**。
        """.trimIndent(),
    ),
    Fixture(
        "图表与 PlantUML",
        """
        ```mermaid
        flowchart LR
          A[开始] --> B{判断}
          B -->|是| C[结束]
        ```

        ```plantuml
        @startuml
        Alice -> Bob: 请求
        Bob --> Alice: 响应
        @enduml
        ```

        ```dot
        digraph G {
          rankdir=LR
          a -> b -> c
        }
        ```
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
    Fixture(
        "代码高亮总览",
        """
        # 代码高亮总览

        逐个语言目视核对着色：是否有关键字/类型/函数/字符串/注释/数字的区分，
        以及语言标签是否出现在顶栏。

        ```kotlin
        // Kotlin：关键字、类型、函数、字符串模板、注解
        @Composable
        fun MessageList(items: List<Message>, onTap: (Int) -> Unit) {
            val count = items.size
            items.forEach { item -> onTap(item.id) }
            println("共 ${'$'}count 条")
        }
        ```

        ```java
        // Java：修饰符、类型、注解
        public final class Repo implements Closeable {
            private static final int MAX = 1024;

            @Override
            public void close() throws IOException { /* 收尾 */ }
        }
        ```

        ```python
        # Python：装饰器、内置常量、f-string
        @dataclass
        class Point:
            x: float
            y: float

            def norm(self) -> float:
                return (self.x ** 2 + self.y ** 2) ** 0.5

        print(f"value = {1 + 2}")  # 注释
        ```

        ```javascript
        // JavaScript：箭头函数、模板串、正则
        const sum = (xs) => xs.reduce((a, b) => a + b, 0);
        const re = /ab+c/gi;
        console.log(`sum=${'$'}{sum([1, 2, 3])}`);
        ```

        ```typescript
        // TypeScript：接口、泛型、类型别名
        interface Box<T> {
            value: T;
            map<U>(f: (t: T) => U): Box<U>;
        }

        type Id = string | number;
        ```

        ```go
        // Go：包、结构体、错误处理
        package main

        type Server struct {
            Addr string
            Port int
        }

        func (s *Server) Run() error {
            if s.Port == 0 {
                return fmt.Errorf("port is zero")
            }
            return nil
        }
        ```

        ```rust
        // Rust：生命周期、匹配、宏
        use std::collections::HashMap;

        fn main() {
            let mut map: HashMap<&str, i32> = HashMap::new();
            map.insert("a", 1);
            match map.get("a") {
                Some(v) => println!("{}", v),
                None => println!("none"),
            }
        }
        ```

        ```c
        /* C：预处理、指针、位运算 */
        #include <stdio.h>

        int main(void) {
            unsigned mask = 0xFFu;
            printf("%u\n", mask & 0x0F);
            return 0;
        }
        ```

        ```cpp
        // C++：模板、命名空间、智能指针
        namespace app {
        template <typename T>
        T clamp(T v, T lo, T hi) {
            return v < lo ? lo : (v > hi ? hi : v);
        }
        }
        ```

        ```csharp
        // C#：特性、属性、可空引用与插值串
        [ApiController]
        public sealed class UserController : ControllerBase
        {
            public string Name { get; init; } = "unknown";

            [HttpGet("/u/{id:int}")]
            public IActionResult Get(int id) => Ok($"{'$'}id");
        }
        ```

        ```php
        <?php
        declare(strict_types=1);

        final class Money
        {
            public function __construct(private int ${'$'}cents) {}

            public function format(): string
            {
                return sprintf("%d.%02d", intdiv(${'$'}this->cents, 100), ${'$'}this->cents % 100);
            }
        }
        ```

        ```ruby
        # Ruby：符号、块、实例变量
        class Order
          def initialize(items)
            @items = items
          end

          def total
            @items.sum { |i| i.price }
          end
        end
        ```

        ```swift
        // Swift：可选链、计算属性、协议
        struct Point: Equatable {
            var x: Double
            var y: Double

            var length: Double { (x * x + y * y).squareRoot() }
        }

        let p = Point(x: 3, y: 4)
        print(p.length)
        ```

        ```dart
        // Dart：空安全、级联、async
        class Repo {
          final String base;
          Repo(this.base);

          Future<String> load(String path) async {
            final url = '${'$'}base${'$'}path';
            return url;
          }
        }
        ```

        ```sql
        -- SQL：DDL/DML、类型、函数
        CREATE TABLE orders (
            id BIGSERIAL PRIMARY KEY,
            user_id INT NOT NULL REFERENCES users (id),
            total DECIMAL(12, 2) DEFAULT 0,
            created_at TIMESTAMP WITH TIME ZONE
        );

        SELECT u.name, COUNT(o.id) AS cnt
        FROM users u LEFT JOIN orders o ON o.user_id = u.id
        WHERE o.created_at >= '2026-01-01'
        GROUP BY u.name
        HAVING COUNT(o.id) > 3;
        ```

        ```json
        {
          "name": "hermes",
          "version": 0.42,
          "enabled": true,
          "tags": ["desktop", "compose"],
          "nested": { "nullValue": null }
        }
        ```

        ```yaml
        # YAML：键、序列、锚点
        service:
          name: hermes
          ports:
            - 9119
            - 8642
          env:
            DEBUG: true
            RETRIES: 3
        ```

        ```toml
        # TOML：表、数组、日期
        [tool.hermes]
        name = "desktop"
        version = "0.4.1"
        ports = [9119, 8642]
        released = 2026-01-01T00:00:00Z
        ```

        ```html
        <!-- HTML：标签、属性、内联样式 -->
        <section class="card" data-id="42">
          <h2 style="color: #333">标题</h2>
          <p>正文 <a href="https://example.com">链接</a></p>
        </section>
        ```

        ```css
        /* CSS：选择器、自定义属性、单位 */
        :root {
          --accent: #7c4dff;
          --radius: 12px;
        }

        .card > h2:hover {
          color: var(--accent);
          border-radius: var(--radius);
        }
        ```

        ```csv
        name,qty,price
        apple,3,1.5
        "banana, ripe",2,0.75
        ```

        ```graphql
        # GraphQL：类型、查询、变量
        type Query {
          message(id: ID!): Message
        }

        query GetMessage(${'$'}id: ID!) {
          message(id: ${'$'}id) { id body author { name } }
        }
        ```

        ```bash
        #!/usr/bin/env bash
        set -euo pipefail

        for f in "${'$'}HOME"/*.log; do
          echo "== ${'$'}f" | tee -a report.txt
        done
        ```

        ```powershell
        # PowerShell：cmdlet、参数、变量
        param([string]${'$'}Path = ".")

        ${'$'}files = Get-ChildItem -Path ${'$'}Path -Filter *.log |
            Where-Object { ${'$'}_.Length -gt 1MB }

        Write-Host "共 ${'$'}(${'$'}files.Count) 个文件" -ForegroundColor Cyan
        ```

        ```dockerfile
        # Dockerfile：指令、环境变量
        FROM eclipse-temurin:25-jdk
        ENV APP_HOME=/app
        WORKDIR ${'$'}APP_HOME
        COPY build/libs/*.jar app.jar
        ENTRYPOINT ["java", "-jar", "app.jar"]
        ```

        ```diff
        --- a/config.json
        +++ b/config.json
        @@ -1,3 +1,3 @@
        -  "theme": "light",
        +  "theme": "dark",
           "zoom": 1.0
        ```

        ```markdown
        # 标题

        - 列表项 **粗体** 与 `行内码`
        > 引用

        [链接](https://example.com)
        ```

        ```ini
        ; INI：节、键值
        [server]
        host = 127.0.0.1
        port = 9119
        ```

        ```properties
        # properties
        app.version=0.4.1
        app.name=hermes
        ```

        ```makefile
        # Makefile：目标、变量、伪目标
        VERSION := 0.4.1

        .PHONY: build
        build:
        	./gradlew :composeApp:packageDistributable
        ```

        ```protobuf
        // Protobuf：消息、枚举、字段号
        syntax = "proto3";

        message Request {
          string id = 1;
          repeated int32 values = 2;
        }
        ```

        以下语言 **SyntaxMP 本身没有**，由本库用 `LanguageExtension` 注册（扩展优先于内置）：

        ```scala
        // Scala：扩展规则 —— 关键字 / 类型 / 注解 / 字符串落色
        @tailrec
        final def loop(n: Int, acc: Int): Int =
          if (n <= 1) acc else loop(n - 1, acc * n)

        case class Point(x: Int, y: Int)

        object Main extends App {
          val p = Point(1, 2)
          println(p.x + p.y)
        }
        ```

        ```lua
        -- Lua：扩展规则 —— 长注释、关键字、内置函数
        local function fib(n)
            if n < 2 then return n end
            return fib(n - 1) + fib(n - 2)
        end

        for i = 1, 10 do
            print(string.format("%d", fib(i)))
        end
        ```

        ```bat
        REM 批处理：扩展规则
        @echo off
        setlocal
        for %%f in (*.log) do echo %%f
        endlocal
        ```

        ```vue
        <!-- Vue：扩展规则（标记语言 + 插值） -->
        <template>
          <div class="app">{{ message }}</div>
        </template>
        ```

        ```xaml
        <!-- XAML：扩展规则（标记语言 + 绑定表达式） -->
        <Window x:Class="App.MainWindow">
          <TextBlock Text="{Binding Name}" />
        </Window>
        ```

        以下语言两边都没有词法器，**保持纯文本**（用于对照，确认不误染）：

        ```haskell
        -- Haskell：预期不着色
        fib :: Int -> Int
        fib n = if n < 2 then n else fib (n - 1) + fib (n - 2)
        ```
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
            // 原始 HTML 块走原生渲染（ksoup 清洗 → Compose 块树），不再需要浏览器。
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
