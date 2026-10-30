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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.shinglem.markdown.MarkdownConfig
import io.github.shinglem.markdown.MarkdownText
import io.github.shinglem.markdown.WebFallback
import io.github.shinglem.markdown.adapter.AdapterDiagnostics
import io.github.shinglem.markdown.adapter.AdapterKind
import io.github.shinglem.markdown.adapter.BlockAdapter
import io.github.shinglem.markdown.adapter.FormatId
import io.github.shinglem.markdown.adapter.Formats
import io.github.shinglem.markdown.model.MdBlock

private data class Fixture(val name: String, val markdown: String, val streaming: Boolean = false)

private val FIXTURES = listOf(
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
 * 演示「自己写一个适配器」：接管数学公式块。
 *
 * 真实项目里这里可以换成 KaTeX/JCEF/原生排版；本 demo 只证明扩展点可用 ——
 * 注册后右侧路由面板会显示 `math → demo.math (Native)`。
 */
private class DemoMathAdapter : BlockAdapter {
    override val id: String = "demo.math"
    override val kind: AdapterKind = AdapterKind.Native
    override val priority: Int = 120

    override fun formats(): Set<FormatId> = setOf(Formats.Math)

    override fun isAvailable(): Boolean = true

    override fun canRender(block: MdBlock): Boolean = block is MdBlock.Math

    @Composable
    override fun Render(block: MdBlock, modifier: Modifier) {
        val math = block as MdBlock.Math
        Column(modifier.padding(vertical = 4.dp)) {
            Text(
                text = "公式（demo 未配置渲染器）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = math.tex,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

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
        MaterialTheme {
            Surface {
                DemoScreen()
            }
        }
    }
}

@Composable
private fun DemoScreen() {
    val selected = remember { mutableStateOf(FIXTURES.first()) }
    val routes = remember { mutableStateMapOf<String, String>() }
    // 按 fixture 名分组：切 fixture 时不清空面板，靠前缀区分，避免「清了却不重填」。
    val currentName = remember { mutableStateOf(FIXTURES.first().name) }

    val config = remember {
        MarkdownConfig(
            adapters = listOf(DemoMathAdapter()),
            // 开关演示：原始 HTML 块交给 JCEF 渲染；运行环境没有 org.cef 时自动退回源码。
            webFallback = WebFallback.Jcef,
            allowRawHtml = true,
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

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(200.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant)) {
            FIXTURES.forEach { fixture ->
                val active = fixture == selected.value
                Text(
                    text = fixture.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            currentName.value = fixture.name
                            selected.value = fixture
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }

        VerticalDivider()

        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(24.dp)) {
            MarkdownText(
                markdown = selected.value.markdown,
                streaming = selected.value.streaming,
                config = config,
            )
        }

        VerticalDivider()

        Column(Modifier.width(360.dp).fillMaxHeight().padding(16.dp)) {
            Text("适配器路由", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "块 → 命中的适配器 (kind)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LazyColumn {
                items(routes.toList().filter { it.first.startsWith("${currentName.value}/") }) { (key, value) ->
                    Text(
                        text = "${key.substringAfter('/')}  $value",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 3.dp),
                    )
                }
            }
        }
    }
}
