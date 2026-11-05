package io.github.shinglem.markdown.parse

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import io.github.shinglem.markdown.adapter.Formats
import io.github.shinglem.markdown.model.AlertKind
import io.github.shinglem.markdown.model.MdBlock
import io.github.shinglem.markdown.text.ALERT_CONTAINER_ALPHA_DARK
import io.github.shinglem.markdown.text.ALERT_CONTAINER_ALPHA_LIGHT
import io.github.shinglem.markdown.text.alertAccentColor
import io.github.shinglem.markdown.text.contrastRatio
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * GitHub Alerts。
 *
 * GFM 解析器原生产出 `ALERT` + `ALERT_TITLE`，所以这里钉住的是「映射是否正确」：
 * 5 种标记都能识别成对应 [AlertKind]、标记不认识时退回普通引用（不能吞内容）、
 * 以及嵌套在列表里的 Alert 也能成为独立块。
 */
class AlertParseTest {

    private val parser = GfmMarkdownParser()

    private fun single(markdown: String): MdBlock = parser.parse(markdown, streaming = false).blocks.single()

    @Test
    fun `五种标记都映射成对应类型`() {
        val expected = mapOf(
            "NOTE" to AlertKind.Note,
            "TIP" to AlertKind.Tip,
            "IMPORTANT" to AlertKind.Important,
            "WARNING" to AlertKind.Warning,
            "CAUTION" to AlertKind.Caution,
        )
        expected.forEach { (marker, kind) ->
            val block = single("> [!$marker]\n> 内容\n")
            assertTrue(block is MdBlock.Alert, "$marker 应解析为 Alert，实际=${block::class.simpleName}")
            assertEquals(kind, block.kind)
        }
    }

    @Test
    fun `标记大小写不敏感`() {
        assertEquals(AlertKind.Note, (single("> [!note]\n> 内容\n") as MdBlock.Alert).kind)
        assertEquals(AlertKind.Caution, (single("> [!Caution]\n> 内容\n") as MdBlock.Alert).kind)
    }

    @Test
    fun `Alert 同时声明 markdown 与 alert 格式`() {
        val block = single("> [!TIP]\n> 内容\n")
        assertEquals(setOf(Formats.Markdown, Formats.Alert), block.formats)
    }

    @Test
    fun `不认识的标记退回普通引用且内容不丢`() {
        val block = single("> [!FOO]\n> 内容\n")
        assertTrue(block is MdBlock.Quote, "未知标记应按引用渲染，实际=${block::class.simpleName}")
        assertTrue(block.raw.contains("内容"))
    }

    @Test
    fun `Alert 正文里的块结构保留`() {
        val block = single(
            """
            > [!NOTE]
            > 正文段落
            >
            > - 列表项
            > - 第二项
            """.trimIndent() + "\n",
        ) as MdBlock.Alert
        val types = block.node.children.map { it.type.toString() }
        assertTrue(types.any { it.contains("PARAGRAPH") }, "应有段落，实际=$types")
        assertTrue(types.any { it.contains("UNORDERED_LIST") }, "应有列表，实际=$types")
    }

    @Test
    fun `嵌套在列表项里的 Alert 也能识别`() {
        val list = parser.parse("- 项目\n\n  > [!WARNING]\n  > 注意\n", streaming = false).blocks.single()
        assertTrue(list is MdBlock.ListBlock)
        val alert = list.node.children
            .flatMap { it.children }
            .first { it.type == GFMElementTypes.ALERT }
        val mapped = MdBlockFactory.of(list.source, alert, "nested")
        assertTrue(mapped is MdBlock.Alert, "嵌套 Alert 应映射为 Alert，实际=${mapped?.let { it::class.simpleName }}")
        assertEquals(AlertKind.Warning, mapped.kind)
    }

    @Test
    fun `Alert 的 strip 后正文不含标记 token`() {
        // 渲染层会过滤 BLOCK_QUOTE / ALERT_TITLE / EOL：这里断言这些 token 确实在节点里，
        // 否则「过滤」是空操作，一旦解析器改变结构就会把 `>` 渲染进正文。
        val block = single("> [!NOTE]\n> 正文\n") as MdBlock.Alert
        val types = block.node.children.map { it.type.toString() }
        assertTrue(types.any { it.contains("BLOCK_QUOTE") }, types.toString())
        assertTrue(types.any { it.contains("ALERT_TITLE") }, types.toString())
    }

    @Test
    fun `五种类型的强调色在亮暗主题下都满足对比度门槛`() {
        val lightBackground = Color(0xFFFDFCF9)
        val darkBackground = Color(0xFF191614)
        AlertKind.entries.forEach { kind ->
            val light = alertAccentColor(kind, dark = false)
            val lightContainer = light.copy(alpha = ALERT_CONTAINER_ALPHA_LIGHT).compositeOver(lightBackground)
            assertTrue(
                contrastRatio(light, lightContainer) >= 4.5f,
                "$kind 亮色对比度不足：${contrastRatio(light, lightContainer)}",
            )
            val dark = alertAccentColor(kind, dark = true)
            val darkContainer = dark.copy(alpha = ALERT_CONTAINER_ALPHA_DARK).compositeOver(darkBackground)
            assertTrue(
                contrastRatio(dark, darkContainer) >= 4.5f,
                "$kind 暗色对比度不足：${contrastRatio(dark, darkContainer)}",
            )
        }
    }

    @Test
    fun `五种类型的强调色互不相同`() {
        val light = AlertKind.entries.map { alertAccentColor(it, dark = false) }
        assertEquals(light.size, light.toSet().size, "亮色强调色重复：$light")
        val dark = AlertKind.entries.map { alertAccentColor(it, dark = true) }
        assertEquals(dark.size, dark.toSet().size, "暗色强调色重复：$dark")
    }
}
