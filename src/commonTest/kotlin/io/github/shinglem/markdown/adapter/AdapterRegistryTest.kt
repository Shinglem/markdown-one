package io.github.shinglem.markdown.adapter

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.shinglem.markdown.MarkdownConfig
import io.github.shinglem.markdown.model.MdBlock
import io.github.shinglem.markdown.parse.GfmMarkdownParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeAdapter(
    override val id: String,
    override val kind: AdapterKind = AdapterKind.Native,
    override val priority: Int = 100,
    private val accepts: Set<FormatId>,
    private val available: Boolean = true,
) : BlockAdapter {
    override fun formats(): Set<FormatId> = accepts
    override fun isAvailable(): Boolean = available
    override fun canRender(block: MdBlock): Boolean = true

    @Composable
    override fun Render(block: MdBlock, modifier: Modifier) = Unit
}

class AdapterRegistryTest {

    private val parser = GfmMarkdownParser()

    private fun codeBlock(language: String): MdBlock =
        parser.parse("```$language\nval x = 1\n```", streaming = false).blocks.single()

    private fun registryOf(vararg adapters: BlockAdapter, config: MarkdownConfig = MarkdownConfig()) =
        AdapterRegistry(adapters.toList() + builtinAdapters(config))

    @Test
    fun `code 通配命中带语言名的代码块`() {
        val registry = AdapterRegistry(listOf(FakeAdapter("code", accepts = setOf(Formats.Code))))
        val picked = registry.select(codeBlock("kotlin"))
        assertEquals("code", picked?.id)
    }

    @Test
    fun `priority 大的先胜`() {
        val registry = AdapterRegistry(
            listOf(
                FakeAdapter("low", priority = 10, accepts = setOf(Formats.Code)),
                FakeAdapter("high", priority = 900, accepts = setOf(Formats.Code)),
            ),
        )
        assertEquals("high", registry.select(codeBlock("kotlin"))?.id)
    }

    @Test
    fun `不可用的适配器被跳过`() {
        val registry = AdapterRegistry(
            listOf(
                FakeAdapter("off", priority = 900, accepts = setOf(Formats.Code), available = false),
                FakeAdapter("on", priority = 10, accepts = setOf(Formats.Code)),
            ),
        )
        assertEquals("on", registry.select(codeBlock("kotlin"))?.id)
    }

    @Test
    fun `没有任何适配器命中时由兜底接管`() {
        val block = codeBlock("kotlin")
        val registry = AdapterRegistry(listOf(SourceFallbackForTest))
        assertEquals("test.fallback", registry.select(block)?.id)
    }

    @Test
    fun `显式注册同 id 覆盖内置`() {
        val custom = FakeAdapter("builtin.source", priority = 500, accepts = setOf(Formats.Any))
        val registry = buildAdapterRegistry(MarkdownConfig(adapters = listOf(custom)))
        assertEquals("builtin.source", registry.select(codeBlock("text"))?.id)
        assertEquals(500, registry.all.first { it.id == "builtin.source" }.priority)
    }

    @Test
    fun `disabledAdapterIds 可以关掉内置适配器`() {
        val registry = buildAdapterRegistry(MarkdownConfig(disabledAdapterIds = setOf("builtin.code")))
        val picked = registry.select(codeBlock("kotlin"))
        assertNotNull(picked)
        assertTrue(picked.id != "builtin.code")
    }

    @Test
    fun `原始 HTML 块优先走原生渲染`() {
        val registry = buildAdapterRegistry(MarkdownConfig())
        val html = parser.parse("<div align=\"center\">hi</div>", streaming = false).blocks.single()
        assertEquals("builtin.html.native", registry.select(html)?.id)
        // 关掉原生渲染就退回「剥壳递归」的兜底实现，仍然不留空白
        val fallback = buildAdapterRegistry(MarkdownConfig(disabledAdapterIds = setOf("builtin.html.native")))
        assertEquals("builtin.html", fallback.select(html)?.id)
    }

    @Test
    fun `脚注块走脚注适配器`() {
        val registry = buildAdapterRegistry(MarkdownConfig())
        val block = parser.parse("正文[^a]\n\n[^a]: 内容\n", streaming = false).blocks.last()
        assertEquals("builtin.footnotes", registry.select(block)?.id)
    }

    @Test
    fun `内置适配器覆盖 mermaid 与普通代码`() {
        val registry = buildAdapterRegistry(MarkdownConfig())
        assertEquals("builtin.diagram", registry.select(codeBlock("mermaid"))?.id)
        assertEquals("builtin.code", registry.select(codeBlock("kotlin"))?.id)
        assertEquals("builtin.code", registry.select(codeBlock(""))?.id)
    }

    @Test
    fun `PlantUML 与 DOT 走图表适配器`() {
        val registry = buildAdapterRegistry(MarkdownConfig())
        assertEquals("builtin.diagram", registry.select(codeBlock("plantuml"))?.id)
        assertEquals("builtin.diagram", registry.select(codeBlock("dot"))?.id)
        assertEquals("builtin.diagram", registry.select(codeBlock("graphviz"))?.id)
    }

    @Test
    fun `没有语言标记但内容是图时走图表适配器`() {
        val registry = buildAdapterRegistry(MarkdownConfig())
        val block = parser.parse("```\ngraph TD\n    A --> B\n```", streaming = false).blocks.single()
        assertEquals("builtin.diagram", registry.select(block)?.id)
    }

    @Test
    fun `带语言标记的普通代码块不会因为内容像图而变成图表`() {
        val registry = buildAdapterRegistry(MarkdownConfig())
        val block = parser.parse("```kotlin\nval s = \"graph TD\"\n```", streaming = false).blocks.single()
        assertEquals("builtin.code", registry.select(block)?.id)
    }

    @Test
    fun `mermaid 块同时声明两个格式`() {
        val block = codeBlock("mermaid")
        assertTrue(Formats.Mermaid in block.formats)
        assertTrue(Formats.code("mermaid") in block.formats)
    }

    @Test
    fun `没有匹配格式时返回空`() {
        val registry = AdapterRegistry(listOf(FakeAdapter("code", accepts = setOf(Formats.Code))))
        val paragraph = parser.parse("普通段落", streaming = false).blocks.single()
        assertNull(registry.select(paragraph))
    }

    @Test
    fun `自定义适配器可接管内置管不到的格式`() {
        val custom = FakeAdapter("demo.math", priority = 120, accepts = setOf(Formats.Math))
        val registry = buildAdapterRegistry(MarkdownConfig(adapters = listOf(custom)))
        val math = parser.parse("$$\nE = mc^2\n$$", streaming = false).blocks.single()
        assertEquals("demo.math", registry.select(math)?.id)
    }
}

private object SourceFallbackForTest : BlockAdapter {
    override val id: String = "test.fallback"
    override val kind: AdapterKind = AdapterKind.Fallback
    override val priority: Int = 0
    override fun formats(): Set<FormatId> = setOf(Formats.Any)
    override fun isAvailable(): Boolean = true
    override fun canRender(block: MdBlock): Boolean = true

    @Composable
    override fun Render(block: MdBlock, modifier: Modifier) = Unit
}
