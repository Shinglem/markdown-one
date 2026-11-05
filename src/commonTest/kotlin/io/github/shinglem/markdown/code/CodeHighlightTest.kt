package io.github.shinglem.markdown.code

import io.github.shinglem.markdown.parse.balanceCodeFences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull

class CodeHighlightTest {
    @Test
    fun `未闭合的围栏在流式渲染时被补齐`() {
        val partial = "```kotlin\nval answer = 42\n"
        assertEquals("$partial```", balanceCodeFences(partial))
    }

    @Test
    fun `已闭合的围栏不重复补齐`() {
        val closed = "```kotlin\nval answer = 42\n```\n\n后续段落"
        assertEquals(closed, balanceCodeFences(closed))
    }

    @Test
    fun `全部闭合且成对出现时保持原样`() {
        val two = "```kotlin\na\n```\n\n```json\n{}\n```"
        assertEquals(two, balanceCodeFences(two))
    }

    @Test
    fun `行内的反引号不计入围栏`() {
        val inline = "普通段落里写 a```b 这种，不应触发补齐"
        assertEquals(inline, balanceCodeFences(inline))
    }

    @Test
    fun `缩进的围栏仍然被识别`() {
        val indented = "1. 步骤\n\n   ```bash\n   ls -la\n"
        assertEquals("$indented```", balanceCodeFences(indented))
    }

    @Test
    fun `json 走内置词法器并有样式`() {
        val code = """{"name": "hermes", "port": 9119, "enabled": true}"""
        val highlighted = highlightCode(code, "json", dark = true)
        assertEquals(code, highlighted.text)
        assertTrue(highlighted.spanStyles.isNotEmpty(), "json 应有着色 span")
    }

    @Test
    fun `bash 经别名归一化后由 highlights 着色`() {
        val code = """if [ -f "${'$'}HOME/.bashrc" ]; then source "${'$'}HOME/.bashrc"; fi"""
        val highlighted = highlightCode(code, "bash", dark = false)
        assertTrue(highlighted.spanStyles.isNotEmpty(), "bash 别名到 SHELL 后应有着色")
    }

    @Test
    fun `powershell 由 syntaxmp 词法器着色`() {
        val code = "\$svc = Get-Service -Name 'HermesGateway'\nif (\$null -eq \$svc) { Write-Host '未注册' }"
        val highlighted = highlightCode(code, "powershell", dark = true)
        assertTrue(highlighted.spanStyles.isNotEmpty(), "powershell 应有着色")
    }

    /** 旧方案（Highlights + 正则）完全覆盖不到的语言，接 SyntaxMP 后必须有着色。 */
    @Test
    fun `syntaxmp 补齐的高频语言都有着色`() {
        val samples = mapOf(
            "csharp" to "[ApiController]\npublic sealed class C : ControllerBase { }",
            "graphql" to "type Query {\n  message(id: ID!): Message\n}",
            "protobuf" to "syntax = \"proto3\";\nmessage R { string id = 1; }",
            "makefile" to "VERSION := 0.4.1\n.PHONY: build",
            "toml" to "[tool.hermes]\nversion = \"0.4.1\"\nports = [9119, 8642]",
            "dockerfile" to "FROM eclipse-temurin:25-jdk\nWORKDIR /app\nCOPY . /app",
        )
        samples.forEach { (language, code) ->
            assertTrue(
                highlightCode(code, language, dark = true).spanStyles.isNotEmpty(),
                "$language 应有着色",
            )
        }
    }

    /** 通过 `LanguageExtension` 注册的语言：SyntaxMP 本身没有，但我们必须能看到高亮。 */
    @Test
    fun `syntaxmp 扩展补齐的语言都有着色`() {
        val samples = mapOf(
            "bat" to "REM 说明\n@echo off\nfor %%f in (*.log) do echo %%f",
            "scala" to "case class Point(x: Int, y: Int)\nobject Main extends App { }",
            "lua" to "-- 注释\nlocal t = { 1, 2 }\nprint(#t)",
            "vue" to "<template>\n  <div class=\"app\">{{ message }}</div>\n</template>",
            "xaml" to "<Window x:Class=\"App\">\n  <TextBlock Text=\"{Binding Name}\" />\n</Window>",
            "jsonc" to "{\n  // 注释\n  \"a\": 1\n}",
            "cfg" to "[core]\nkey = 1",
        )
        samples.forEach { (language, code) ->
            assertTrue(
                highlightCode(code, language, dark = true).spanStyles.isNotEmpty(),
                "$language 应有着色",
            )
        }
    }

    /** 两边都没有的语言必须保持纯文本，不能被别的规则误染。 */
    @Test
    fun `没有词法器的语言保持纯文本`() {
        val code = "case class Point(x: Int, y: Int)"
        assertTrue(highlightCode(code, "mermaid", dark = true).spanStyles.isEmpty())
        assertTrue(highlightCode(code, "haskell", dark = true).spanStyles.isEmpty())
        assertEquals(code, highlightCode(code, "haskell", dark = true).text)
    }

    @Test
    fun `不认识的语言不着色也不误染`() {
        val code = "flowchart LR\n  classDef ok fill:#1f6feb\n  class G,S ok"
        val highlighted = highlightCode(code, "mermaid", dark = true)
        assertEquals(code, highlighted.text)
        assertTrue(highlighted.spanStyles.isEmpty(), "mermaid 不应被当成 C 系语法误染")
    }

    @Test
    fun `缺少语言标注时不着色`() {
        val highlighted = highlightCode("echo hello", null, dark = true)
        assertTrue(highlighted.spanStyles.isEmpty())
        assertNull(highlighted.spanStyles.firstOrNull())
    }

    @Test
    fun `语言别名大小写与花括号写法都能识别`() {
        assertTrue(isHighlightable("JSON"))
        assertTrue(isHighlightable("{.yaml}"))
        assertTrue(isHighlightable("kt"))
        assertFalse(isHighlightable("mermaid"))
        assertFalse(isHighlightable(null))
    }
}
