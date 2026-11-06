package io.github.shinglem.markdown.parse

import io.github.shinglem.markdown.model.FootnoteItem
import io.github.shinglem.markdown.model.MdBlock
import io.github.shinglem.markdown.model.MdDocument
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser as JetBrainsMarkdownParser

/**
 * 基于 JetBrains markdown（GFM）的解析实现。
 *
 * 只负责「源码 → 块序列」，块内部的 inline 结构保持为 AST 节点，
 * 由默认块渲染器（`text/MdInline.kt` 的行内构建器）消费。
 */
public class GfmMarkdownParser : MarkdownParser {

    private val flavour = GFMFlavourDescriptor()

    @Suppress("DEPRECATION")
    private val parser = JetBrainsMarkdownParser(flavour)

    override fun parse(source: String, streaming: Boolean): MdDocument {
        val balanced = if (streaming) balanceCodeFences(source) else source
        // 脚注定义与正文引用：必须在解析前抽出并改写，见 FootnoteNormalizer 的说明。
        val footnotes = preprocessFootnotes(balanced)
        // 内联 HTML → Markdown 标记：必须在解析前做，见 HtmlNormalizer 的说明。
        // 块级 `$$…$$` → ```math 围栏：必须在解析前做，否则块内像列表的行会把公式拆散
        // （见 MathFenceNormalizer 的说明）。
        val text = normalizeMathBlocks(normalizeInlineHtml(footnotes.text))
        val tree = runCatching { parser.buildMarkdownTreeFromString(text as CharSequence) }.getOrNull()
            ?: return MdDocument(emptyList(), streaming)

        val blocks = buildList {
            tree.children.forEachIndexed { index, node ->
                MdBlockFactory.of(text, node, "b$index")?.let(::add)
            }
        }
        val merged = mergeDetails(blocks, text)
        if (footnotes.definitions.isEmpty()) return MdDocument(merged, streaming)
        val items = footnotes.definitions.map { FootnoteItem(it.id, it.number, it.markdown) }
        return MdDocument(
            merged + MdBlock.Footnotes(
                key = "footnotes",
                source = text,
                raw = footnotes.definitions.joinToString("\n") { "[^${it.id}]: ${it.markdown}" },
                node = tree,
                items = items,
            ),
            streaming,
        )
    }

    /**
     * 把 `<details>…</details>` 之间被 GFM 拆成多块的内容合并成一个 `Custom("details")` 块。
     *
     * GFM 的 HTML block 以空行为界：`<details>`、中间的列表、`</details>` 会变成三个块，
     * 结果是标签各占一行、内容却按 markdown 渲染了 —— 折叠块因此完全不起作用。
     * 这里按「遇到 `<details` 开始、遇到 `</details>` 结束」把中间所有块拼回去。
     */
    private fun mergeDetails(blocks: List<MdBlock>, source: String): List<MdBlock> {
        if (blocks.none { it.raw.contains("<details", ignoreCase = true) }) return blocks
        val out = mutableListOf<MdBlock>()
        var i = 0
        while (i < blocks.size) {
            val block = blocks[i]
            val opensHere = block.raw.contains("<details", ignoreCase = true)
            if (!opensHere) {
                out += block
                i++
                continue
            }
            val builder = StringBuilder()
            var j = i
            var closed = false
            while (j < blocks.size) {
                val part = blocks[j]
                builder.append(part.raw)
                val closesHere = part.raw.contains("</details>", ignoreCase = true)
                j++
                if (closesHere) {
                    closed = true
                    break
                }
                builder.append("\n\n")
            }
            if (!closed) {
                // 没有闭合（流式输出中途）：保持原样，避免把后文吞掉。
                out += block
                i++
                continue
            }
            out += MdBlock.Custom(
                key = block.key,
                source = source,
                raw = builder.toString(),
                node = block.node,
                type = "details",
            )
            i = j
        }
        return out
    }
}

