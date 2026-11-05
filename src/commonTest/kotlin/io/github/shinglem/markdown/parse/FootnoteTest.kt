package io.github.shinglem.markdown.parse

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.BaselineShift
import io.github.shinglem.markdown.adapter.Formats
import io.github.shinglem.markdown.model.MdBlock
import io.github.shinglem.markdown.text.MdInlineStyles
import io.github.shinglem.markdown.text.buildMdInline
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 脚注（`[^1]` + `[^1]: …`）。
 *
 * 脚注不是 CommonMark/GFM 规范能力（GitHub 是产品特性），AST 层没有节点、
 * 连续多条定义还会被合并成段落 —— 所以实现放在解析前预处理里，这里钉住它的四条约定：
 * 定义被抽出、引用被改写、代码里的内容不动、未定义时保持字面量。
 */
class FootnoteTest {

    private val parser = GfmMarkdownParser()

    private val styles = MdInlineStyles(
        link = TextLinkStyles(SpanStyle(color = Color.Blue)),
        code = SpanStyle(),
        highlight = SpanStyle(),
        footnote = SpanStyle(baselineShift = BaselineShift.Superscript),
        references = emptyMap(),
        linkListener = null,
    )

    @Test
    fun `定义被抽出且正文里的引用改写成上标链接`() {
        val result = preprocessFootnotes("正文[^a]。\n\n[^a]: 脚注内容\n")
        // 定义占的行被换成空行（避免前后段落被合并），所以只比较去掉空行后的正文
        assertEquals("正文[1](#fn-a)。", result.text.trim())
        assertEquals(1, result.definitions.size)
        val definition = result.definitions.single()
        assertEquals("a", definition.id)
        assertEquals(1, definition.number)
        assertEquals("脚注内容", definition.markdown)
    }

    @Test
    fun `连续多行定义分别抽出`() {
        val result = preprocessFootnotes("甲[^a]乙[^b]\n\n[^a]: 第一\n[^b]: 第二\n")
        assertEquals(listOf("a", "b"), result.definitions.map { it.id })
        assertEquals(listOf("第一", "第二"), result.definitions.map { it.markdown })
        assertEquals("甲[1](#fn-a)乙[2](#fn-b)", result.text.trim())
    }

    @Test
    fun `缩进续行并入同一条定义`() {
        val result = preprocessFootnotes("正文[^a]\n\n[^a]: 第一行\n  第二行\n")
        assertEquals("第一行\n第二行", result.definitions.single().markdown)
    }

    @Test
    fun `空行后的缩进行作为第二段`() {
        val result = preprocessFootnotes("正文[^a]\n\n[^a]: 第一段\n\n  第二段\n")
        assertEquals("第一段\n\n第二段", result.definitions.single().markdown)
    }

    @Test
    fun `编号按首次引用顺序而不是定义顺序`() {
        val result = preprocessFootnotes("先引用 b[^b]，再引用 a[^a]\n\n[^a]: A\n[^b]: B\n")
        assertTrue(result.text.contains("[1](#fn-b)"), result.text)
        assertTrue(result.text.contains("[2](#fn-a)"), result.text)
        assertEquals(listOf("b", "a"), result.definitions.map { it.id })
    }

    @Test
    fun `未被引用的定义也保留并排在最后`() {
        val result = preprocessFootnotes("正文[^a]\n\n[^a]: A\n[^z]: Z\n")
        assertEquals(listOf("a", "z"), result.definitions.map { it.id })
        assertEquals(listOf(1, 2), result.definitions.map { it.number })
    }

    @Test
    fun `重复引用同一个 id 只占一个编号`() {
        val result = preprocessFootnotes("甲[^a]乙[^a]\n\n[^a]: A\n")
        assertEquals(1, result.definitions.size)
        assertEquals("甲[1](#fn-a)乙[1](#fn-a)", result.text.trim())
    }

    @Test
    fun `围栏代码块与行内代码里的内容不动`() {
        val source = "```\n[^a]: 不是定义\n```\n\n用 `[^a]` 示意\n"
        assertEquals(source, preprocessFootnotes(source).text)
    }

    @Test
    fun `没有定义时原样返回且引用保持字面量`() {
        val source = "正文[^missing]\n"
        val result = preprocessFootnotes(source)
        assertEquals(source, result.text)
        assertTrue(result.definitions.isEmpty())
    }

    @Test
    fun `未定义的引用保持字面量而不是变成空标记`() {
        val result = preprocessFootnotes("甲[^a]乙[^nope]\n\n[^a]: A\n")
        assertTrue(result.text.contains("[^nope]"), result.text)
        assertTrue(result.text.contains("[1](#fn-a)"), result.text)
    }

    @Test
    fun `解析器在文末产出脚注块`() {
        val document = parser.parse("正文[^a]\n\n[^a]: 脚注内容\n", streaming = false)
        val footnotes = document.blocks.last()
        assertTrue(footnotes is MdBlock.Footnotes, "最后一个块应是脚注，实际=${footnotes::class.simpleName}")
        assertEquals(listOf("脚注内容"), footnotes.items.map { it.markdown })
        assertEquals(setOf(Formats.Markdown, Formats.Footnotes), footnotes.formats)
    }

    @Test
    fun `没有定义时解析结果里没有脚注块`() {
        val document = parser.parse("正文\n\n[^a]: 只有定义没有引用\n", streaming = false)
        assertTrue(document.blocks.none { it is MdBlock.Footnotes } || document.blocks.last() is MdBlock.Footnotes)
    }

    @Test
    fun `引用在渲染层落成上标样式`() {
        val source = preprocessFootnotes("正文[^a]。\n\n[^a]: A\n").text
        val tree = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(source)
        val paragraph = tree.children.first { it.type.toString().contains("PARAGRAPH") }
        val inline = buildMdInline(source, paragraph, styles)
        // `[^a]` 的 `[`、`^`、`a`、`]` 不该留在文本里，只剩序号 `1`
        assertEquals("正文1。", inline.text.text)
        val superscript = inline.text.spanStyles.filter { it.item.baselineShift == BaselineShift.Superscript }
        assertTrue(superscript.isNotEmpty(), "脚注序号应是上标，实际=${inline.text.spanStyles}")
        assertEquals("1", inline.text.text.substring(superscript.first().start, superscript.first().end))
    }
}
