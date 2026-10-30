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
        val picked = registry.select(codeBlock("kotlin"), streaming = false, allowJcef = true)
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
        assertEquals("high", registry.select(codeBlock("kotlin"), streaming = false)?.id)
    }

    @Test
    fun `不可用的适配器被跳过`() {
        val registry = AdapterRegistry(
            listOf(
                FakeAdapter("off", priority = 900, accepts = setOf(Formats.Code), available = false),
                FakeAdapter("on", priority = 10, accepts = setOf(Formats.Code)),
            ),
        )
        assertEquals("on", registry.select(codeBlock("kotlin"), streaming = false)?.id)
    }

    @Test
    fun `流式期间 JCEF 适配器不参与`() {
        val registry = AdapterRegistry(
            listOf(
                FakeAdapter("web", kind = AdapterKind.Jcef, priority = 900, accepts = setOf(Formats.Code)),
                FakeAdapter("native", priority = 10, accepts = setOf(Formats.Code)),
            ),
        )
        assertEquals("web", registry.select(codeBlock("kotlin"), streaming = false)?.id)
        assertEquals("native", registry.select(codeBlock("kotlin"), streaming = true)?.id)
    }

    @Test
    fun `没有任何适配器命中时由兜底接管`() {
        val block = codeBlock("kotlin")
        val registry = AdapterRegistry(listOf(SourceFallbackForTest))
        assertEquals("test.fallback", registry.select(block, streaming = false)?.id)
    }

    @Test
    fun `显式注册同 id 覆盖内置`() {
        val custom = FakeAdapter("builtin.source", priority = 500, accepts = setOf(Formats.Any))
        val registry = buildAdapterRegistry(MarkdownConfig(adapters = listOf(custom)))
        assertEquals("builtin.source", registry.select(codeBlock("text"), streaming = false)?.id)
        assertEquals(500, registry.all.first { it.id == "builtin.source" }.priority)
    }

    @Test
    fun `disabledAdapterIds 可以关掉内置适配器`() {
        val registry = buildAdapterRegistry(MarkdownConfig(disabledAdapterIds = setOf("builtin.code")))
        val picked = registry.select(codeBlock("kotlin"), streaming = false)
        assertNotNull(picked)
        assertTrue(picked.id != "builtin.code")
    }

    @Test
    fun `内置适配器覆盖 mermaid 与普通代码`() {
        val registry = buildAdapterRegistry(MarkdownConfig())
        assertEquals("builtin.mermaid", registry.select(codeBlock("mermaid"), streaming = false)?.id)
        assertEquals("builtin.code", registry.select(codeBlock("kotlin"), streaming = false)?.id)
        assertEquals("builtin.code", registry.select(codeBlock(""), streaming = false)?.id)
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
        assertNull(registry.select(paragraph, streaming = false))
    }

    @Test
    fun `自定义适配器可接管内置管不到的格式`() {
        val custom = FakeAdapter("demo.math", priority = 120, accepts = setOf(Formats.Math))
        val registry = buildAdapterRegistry(MarkdownConfig(adapters = listOf(custom)))
        val math = parser.parse("$$\nE = mc^2\n$$", streaming = false).blocks.single()
        assertEquals("demo.math", registry.select(math, streaming = false)?.id)
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
