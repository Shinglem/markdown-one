package io.github.shinglem.markdown.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import io.github.shinglem.markdown.parse.normalizeInlineHtml
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 图片尺寸透传。
 *
 * 内联 SVG 的两条入口（`![](x.svg)` 与 `<img src="x.svg">`）最终都落到同一个 Markdown 图片节点，
 * Coil 的 `coil-svg` 会自动识别 SVG；这里只钉住「`width` / `height` 不丢」这条链路：
 * `HtmlNormalizer` 塞进 title → 行内构建器读出 → 占位框按它约束。
 */
class ImageSizeTest {

    private val styles = MdInlineStyles(
        link = TextLinkStyles(SpanStyle(color = Color.Blue)),
        code = SpanStyle(),
        highlight = SpanStyle(),
        footnote = SpanStyle(),
        references = emptyMap(),
        linkListener = null,
    )

    private fun images(markdown: String): List<MdImageRef> {
        val source = normalizeInlineHtml(markdown)
        val tree = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(source)
        val paragraph = tree.children.first { it.type == MarkdownElementTypes.PARAGRAPH }
        return buildMdInline(source, paragraph, styles).images
    }

    @Test
    fun `解析图片 title 里的尺寸`() {
        assertEquals(ImageSize(120, 40), parseImageSize("\"size=120x40\""))
        assertEquals(ImageSize(120, null), parseImageSize("size=120x"))
        assertEquals(ImageSize(null, 40), parseImageSize("'size=x40'"))
        assertNull(parseImageSize(null))
        assertNull(parseImageSize("普通标题"))
        assertNull(parseImageSize("size=0x0"))
    }

    @Test
    fun `markdown 图片语法也能带尺寸`() {
        val image = images("""![](https://example.com/a.svg "size=32x32")""").single()
        assertEquals(32, image.widthPx)
        assertEquals(32, image.heightPx)
    }

    @Test
    fun `img 标签的宽高穿过改写落到行内图片`() {
        val image = images("""<img src="https://example.com/b.svg" alt="logo" width="64" height="24">""").single()
        assertEquals("https://example.com/b.svg", image.url)
        assertEquals("logo", image.alt)
        assertEquals(64, image.widthPx)
        assertEquals(24, image.heightPx)
    }

    @Test
    fun `没有尺寸属性时保持原样`() {
        val image = images("![logo](https://example.com/c.svg)").single()
        assertNull(image.widthPx)
        assertNull(image.heightPx)
        assertNotNull(image.alt)
    }
}
