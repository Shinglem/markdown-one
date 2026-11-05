package io.github.shinglem.markdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import io.github.shinglem.markdown.model.MdBlock
import io.github.shinglem.markdown.parse.GfmMarkdownParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 独占一行的图片要升级成块级块（[MdBlock.Image]），才能限高并挂点击放大。
 *
 * 以前这类图片落在 `Paragraph` 里走行内渲染：既没有限高，也没有任何交互。
 */
@OptIn(ExperimentalTestApi::class)
class ImageBlockTest {

    private fun blocks(markdown: String) = GfmMarkdownParser().parse(markdown, streaming = false).blocks

    @Test
    fun `独占一行的图片解析为块级 Image`() {
        val block = blocks("![架构图](https://example.com/a.png)").single()
        val image = assertIs<MdBlock.Image>(block)
        assertEquals("https://example.com/a.png", image.url)
        assertEquals("架构图", image.alt)
    }

    @Test
    fun `带 title 的图片也能取出地址`() {
        val block = blocks("""![alt](https://example.com/a.png "标题")""").single()
        assertEquals("https://example.com/a.png", assertIs<MdBlock.Image>(block).url)
    }

    @Test
    fun `与文字混排的图片仍走段落`() {
        val block = blocks("看这张图 ![alt](https://example.com/a.png) 就懂了").single()
        assertIs<MdBlock.Paragraph>(block)
    }

    @Test
    fun `一行里有两个图片时不升级为块级`() {
        val block = blocks("![](https://example.com/a.png) ![](https://example.com/b.png)").single()
        assertIs<MdBlock.Paragraph>(block)
    }

    @Test
    fun `块级图片渲染后不残留 Markdown 源码`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MarkdownText("![架构图](https://example.com/a.png)")
            }
        }
        waitForIdle()
        val texts = mutableListOf<String>()
        fun walk(node: SemanticsNode) {
            node.config.getOrNull(SemanticsProperties.Text)?.forEach { texts += it.text }
            node.children.forEach(::walk)
        }
        walk(onRoot().fetchSemanticsNode())
        assertTrue(
            texts.none { it.contains("![") },
            "块级图片不该以 Markdown 源码出现，实际=$texts",
        )
    }
}
