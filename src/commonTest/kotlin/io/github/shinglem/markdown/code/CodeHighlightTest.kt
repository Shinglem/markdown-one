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
    fun `powershell 走内置词法器`() {
        val code = "\$svc = Get-Service -Name 'HermesGateway'\nif (\$null -eq \$svc) { Write-Host '未注册' }"
        val highlighted = highlightCode(code, "powershell", dark = true)
        assertTrue(highlighted.spanStyles.isNotEmpty(), "powershell 应有着色")
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
