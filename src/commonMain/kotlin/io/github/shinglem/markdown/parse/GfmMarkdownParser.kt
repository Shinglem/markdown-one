package io.github.shinglem.markdown.parse

import io.github.shinglem.markdown.model.MdBlock
import io.github.shinglem.markdown.model.MdDocument
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser as JetBrainsMarkdownParser

/**
 * 基于 JetBrains markdown（GFM）的解析实现。
 *
 * 只负责「源码 → 块序列」，块内部的 inline 结构保持为 AST 节点，
 * 由默认块渲染器（mikepenz 的 element composable）消费。
 */
public class GfmMarkdownParser : MarkdownParser {

    private val flavour = GFMFlavourDescriptor()

    @Suppress("DEPRECATION")
    private val parser = JetBrainsMarkdownParser(flavour)

    override fun parse(source: String, streaming: Boolean): MdDocument {
        val text = if (streaming) balanceCodeFences(source) else source
        val tree = runCatching { parser.buildMarkdownTreeFromString(text as CharSequence) }.getOrNull()
            ?: return MdDocument(emptyList(), streaming)

        val blocks = buildList {
            tree.children.forEachIndexed { index, node ->
                toBlock(text, node, index)?.let(::add)
            }
        }
        return MdDocument(blocks, streaming)
    }

    private fun raw(text: String, node: ASTNode): String = text.substring(node.startOffset, node.endOffset)

    private fun toBlock(text: String, node: ASTNode, index: Int): MdBlock? {
        val key = "b$index"
        val rawText = raw(text, node)
        return when (node.type) {
            MarkdownElementTypes.ATX_1, MarkdownElementTypes.SETEXT_1 ->
                MdBlock.Heading(key, text, rawText, node, 1)

            MarkdownElementTypes.ATX_2, MarkdownElementTypes.SETEXT_2 ->
                MdBlock.Heading(key, text, rawText, node, 2)

            MarkdownElementTypes.ATX_3 -> MdBlock.Heading(key, text, rawText, node, 3)
            MarkdownElementTypes.ATX_4 -> MdBlock.Heading(key, text, rawText, node, 4)
            MarkdownElementTypes.ATX_5 -> MdBlock.Heading(key, text, rawText, node, 5)
            MarkdownElementTypes.ATX_6 -> MdBlock.Heading(key, text, rawText, node, 6)

            MarkdownElementTypes.CODE_FENCE -> codeBlock(key, text, rawText, node, fenced = true)
            MarkdownElementTypes.CODE_BLOCK -> codeBlock(key, text, rawText, node, fenced = false)

            MarkdownElementTypes.BLOCK_QUOTE -> MdBlock.Quote(key, text, rawText, node)
            MarkdownElementTypes.UNORDERED_LIST -> MdBlock.ListBlock(key, text, rawText, node, ordered = false)
            MarkdownElementTypes.ORDERED_LIST -> MdBlock.ListBlock(key, text, rawText, node, ordered = true)
            GFMElementTypes.TABLE -> MdBlock.Table(key, text, rawText, node)
            MarkdownTokenTypes.HORIZONTAL_RULE -> MdBlock.Rule(key, text, rawText, node)
            MarkdownElementTypes.HTML_BLOCK -> MdBlock.Html(key, text, rawText, node)

            MarkdownElementTypes.PARAGRAPH ->
                mathBlock(key, text, rawText, node) ?: MdBlock.Paragraph(key, text, rawText, node)

            // 空白等无渲染意义的节点直接丢弃
            MarkdownTokenTypes.EOL, MarkdownTokenTypes.WHITE_SPACE -> null

            else -> MdBlock.Paragraph(key, text, rawText, node)
        }
    }

    private fun codeBlock(key: String, text: String, rawText: String, node: ASTNode, fenced: Boolean): MdBlock.CodeBlock {
        val language = node.children
            .firstOrNull { it.type == MarkdownTokenTypes.FENCE_LANG }
            ?.let { text.substring(it.startOffset, it.endOffset).trim() }
            ?.takeIf { it.isNotEmpty() }
        val code = if (fenced) stripFences(rawText) else rawText.trimEnd('\n')
        return MdBlock.CodeBlock(key, text, rawText, node, code = code, language = language, fenced = fenced)
    }

    /** 去掉首行 ```lang 与末行 ```。 */
    private fun stripFences(rawText: String): String {
        val lines = rawText.lines()
        if (lines.isEmpty()) return ""
        val start = if (lines.first().trimStart().startsWith("```")) 1 else 0
        val end = if (lines.size > start && lines.last().trim().startsWith("```")) lines.size - 1 else lines.size
        return lines.subList(start, end).joinToString("\n")
    }

    /** `$$ ... $$` 整段公式。GFM 不识别，故在段落层做一次朴素判定。 */
    private fun mathBlock(key: String, text: String, rawText: String, node: ASTNode): MdBlock.Math? {
        val trimmed = rawText.trim()
        if (!(trimmed.startsWith("$$") && trimmed.endsWith("$$") && trimmed.length > 4)) return null
        return MdBlock.Math(key, text, rawText, node, tex = trimmed.removeSurrounding("$$").trim(), display = true)
    }
}
