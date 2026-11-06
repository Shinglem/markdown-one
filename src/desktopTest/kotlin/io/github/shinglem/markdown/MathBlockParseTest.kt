package io.github.shinglem.markdown

import io.github.shinglem.markdown.model.MdBlock
import io.github.shinglem.markdown.parse.GfmMarkdownParser
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 真机会话里的块级公式必须被识别成 [MdBlock.Math]。
 *
 * 如果落到普通段落，`\sum_{i=1}` 里的 `_` 会被当成强调标记吃掉、`&` 会变实体，
 * 看起来就像「公式没渲染」—— 与 ratex 能不能排版是两件事，必须分开判定。
 */
class MathBlockParseTest {

    private val telescope = """
        ## 二、20 项分式求和（望远镜求和）

        $$
        \begin{aligned}
        \sum_{i=1}^{20}\frac{1}{i(i+1)}
        &= \frac{1}{1\cdot 2}+\frac{1}{2\cdot 3}+\frac{1}{3\cdot 4}+\cdots+\frac{1}{20\cdot 21}\\[2pt]
        &= \left(\frac{1}{1}-\frac{1}{2}\right)
         + \left(\frac{1}{2}-\frac{1}{3}\right)
         + \cdots
         + \left(\frac{1}{20}-\frac{1}{21}\right)\\[2pt]
        &= 1-\frac{1}{21}
         = \frac{20}{21}
         \approx 0.952381
        \end{aligned}
        $$
    """.trimIndent()

    @Test
    fun `望远镜求和的块级公式被识别成 Math 块`() {
        val doc = GfmMarkdownParser().parse(telescope, streaming = false)
        val kinds = doc.blocks.map { it::class.simpleName }
        assertTrue(
            doc.blocks.any { it is MdBlock.Math },
            "没有 Math 块，实际块序列=$kinds",
        )
    }

    @Test
    fun `行距参数不破坏公式块识别`() {
        val md = "$$\n\\begin{aligned}\na &= b\\\\[2pt]\nc &= d\n\\end{aligned}\n$$"
        val doc = GfmMarkdownParser().parse(md, streaming = false)
        val kinds = doc.blocks.map { it::class.simpleName }
        assertTrue(doc.blocks.any { it is MdBlock.Math }, "没有 Math 块，实际块序列=$kinds")
    }

    @Test
    fun `依赖 Math 块解析并渲染多行公式`() {
        val doc = GfmMarkdownParser().parse(telescope, streaming = false)
        val math = doc.blocks.filterIsInstance<MdBlock.Math>()
        assertTrue(math.isNotEmpty(), "没有 Math 块")
        assertTrue(math.first().tex.contains("aligned"), "tex 提取不完整：${math.first().tex.take(120)}")
    }
}
