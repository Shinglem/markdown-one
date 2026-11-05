package io.github.shinglem.markdown.parse

import io.github.shinglem.markdown.model.AlertKind
import io.github.shinglem.markdown.model.MdBlock
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

/**
 * 单个 AST 节点 → [MdBlock] 的映射。
 *
 * 解析器用它产出顶层块；渲染层用它把「嵌套在引用/列表里的块」交回同一条适配器链
 * （`text/MdBlocks.kt` 的 `MdRoutedBlock`）。两处共用一份映射，避免再出现一套写死的 `when`。
 */
internal object MdBlockFactory {

    /** 返回 null 表示这个节点没有渲染意义（空白、EOL）。 */
    fun of(source: String, node: ASTNode, key: String): MdBlock? {
        val rawText = source.substring(node.startOffset, node.endOffset)
        return when (node.type) {
            MarkdownElementTypes.ATX_1, MarkdownElementTypes.SETEXT_1 ->
                MdBlock.Heading(key, source, rawText, node, 1)

            MarkdownElementTypes.ATX_2, MarkdownElementTypes.SETEXT_2 ->
                MdBlock.Heading(key, source, rawText, node, 2)

            MarkdownElementTypes.ATX_3 -> MdBlock.Heading(key, source, rawText, node, 3)
            MarkdownElementTypes.ATX_4 -> MdBlock.Heading(key, source, rawText, node, 4)
            MarkdownElementTypes.ATX_5 -> MdBlock.Heading(key, source, rawText, node, 5)
            MarkdownElementTypes.ATX_6 -> MdBlock.Heading(key, source, rawText, node, 6)

            MarkdownElementTypes.CODE_FENCE -> codeBlock(key, source, rawText, node, fenced = true)
            MarkdownElementTypes.CODE_BLOCK -> codeBlock(key, source, rawText, node, fenced = false)

            MarkdownElementTypes.BLOCK_QUOTE -> MdBlock.Quote(key, source, rawText, node)
            // GFM 原生识别 `> [!NOTE]`；不认识的标记按普通引用渲染（与 GitHub 一致）
            GFMElementTypes.ALERT -> alert(key, source, rawText, node)
                ?: MdBlock.Quote(key, source, rawText, node)
            MarkdownElementTypes.UNORDERED_LIST -> MdBlock.ListBlock(key, source, rawText, node, ordered = false)
            MarkdownElementTypes.ORDERED_LIST -> MdBlock.ListBlock(key, source, rawText, node, ordered = true)
            GFMElementTypes.TABLE -> MdBlock.Table(key, source, rawText, node)
            MarkdownTokenTypes.HORIZONTAL_RULE -> MdBlock.Rule(key, source, rawText, node)
            MarkdownElementTypes.HTML_BLOCK -> MdBlock.Html(key, source, rawText, node)

            MarkdownElementTypes.PARAGRAPH ->
                imageBlock(key, source, rawText, node)
                    ?: mathBlock(key, source, rawText, node)
                    ?: MdBlock.Paragraph(key, source, rawText, node)

            // GFM 0.7.17 原生识别 `$$…$$`，比在段落层做文本判定可靠
            GFMElementTypes.BLOCK_MATH -> MdBlock.Math(
                key = key,
                source = source,
                raw = rawText,
                node = node,
                tex = mathTex(rawText),
                display = true,
            )

            // 空白等无渲染意义的节点直接丢弃
            MarkdownTokenTypes.EOL, MarkdownTokenTypes.WHITE_SPACE -> null

            else -> MdBlock.Paragraph(key, source, rawText, node)
        }
    }

    /**
     * 独占一行的图片 `![](url)` → [MdBlock.Image]。
     *
     * GFM 把独占一行的图片解析成 `PARAGRAPH`（内含 `IMAGE`），所以以前这类图片走行内渲染：
     * 既拿不到 `BlockViewport` 的限高缩放，也没法挂「点击放大」。这里在段落层识别出
     * 「只有一个图片、没有其它文本」的情况，升级成块级。
     *
     * 与文字混排的图片（`文字 ![alt](url) 文字`）仍走段落 → 行内，行为不变。
     */
    private fun imageBlock(key: String, source: String, rawText: String, node: ASTNode): MdBlock.Image? {
        val meaningful = node.children.filterNot {
            it.type == MarkdownTokenTypes.EOL || it.type == MarkdownTokenTypes.WHITE_SPACE
        }
        val image = meaningful.singleOrNull()?.takeIf { it.type == MarkdownElementTypes.IMAGE } ?: return null
        // url 取 LINK_DESTINATION 原文（`<...>` 包裹形式要剥掉）；取不到就不是有效的图片块
        val url = image.childOfType(MarkdownElementTypes.LINK_DESTINATION)
            ?.let { source.substring(it.startOffset, it.endOffset).trim().removeSurrounding("<", ">") }
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val alt = image.childOfType(MarkdownElementTypes.LINK_TEXT)
            ?.let { source.substring(it.startOffset, it.endOffset).trim().removeSurrounding("[", "]") }
            ?.takeIf { it.isNotBlank() }
            .orEmpty()
        return MdBlock.Image(key, source, rawText, node, url, alt)
    }

    private fun ASTNode.childOfType(type: org.intellij.markdown.IElementType): ASTNode? {
        children.forEach { child ->
            if (child.type == type) return child
            child.childOfType(type)?.let { return it }
        }
        return null
    }

    /**
     * `ALERT` 节点 → [MdBlock.Alert]。
     *
     * 标题是 `ALERT_TITLE` token（文本形如 `[!NOTE]`）；标记不认识时返回 null，
     * 由调用方按普通引用处理。
     */
    private fun alert(key: String, source: String, rawText: String, node: ASTNode): MdBlock.Alert? {
        val title = node.children
            .firstOrNull { it.type == GFMTokenTypes.ALERT_TITLE }
            ?.let { source.substring(it.startOffset, it.endOffset) }
            ?.trim()
            ?: return null
        val kind = AlertKind.fromMarker(title.removePrefix("[!").removeSuffix("]")) ?: return null
        return MdBlock.Alert(key, source, rawText, node, kind)
    }

    private fun codeBlock(
        key: String,
        source: String,
        rawText: String,
        node: ASTNode,
        fenced: Boolean,
    ): MdBlock.CodeBlock {
        val language = node.children
            .firstOrNull { it.type == MarkdownTokenTypes.FENCE_LANG }
            ?.let { source.substring(it.startOffset, it.endOffset).trim() }
            ?.takeIf { it.isNotEmpty() }
        val code = if (fenced) stripFences(rawText) else rawText.trimEnd('\n')
        return MdBlock.CodeBlock(key, source, rawText, node, code = code, language = language, fenced = fenced)
    }

    /** 去掉首行 ```lang 与末行 ```。 */
    private fun stripFences(rawText: String): String {
        val lines = rawText.lines()
        if (lines.isEmpty()) return ""
        val start = if (lines.first().trimStart().startsWith("```")) 1 else 0
        val end = if (lines.size > start && lines.last().trim().startsWith("```")) lines.size - 1 else lines.size
        return lines.subList(start, end).joinToString("\n")
    }

    /** `$$ ... $$` 整段公式（段落层兜底，GFM 未产出 `BLOCK_MATH` 时用）。 */
    private fun mathBlock(key: String, source: String, rawText: String, node: ASTNode): MdBlock.Math? {
        val trimmed = rawText.trim()
        if (!(trimmed.startsWith("$$") && trimmed.endsWith("$$") && trimmed.length > 4)) return null
        return MdBlock.Math(key, source, rawText, node, tex = mathTex(rawText), display = true)
    }

    /**
     * `$$…$$` 里的 TeX 正文。
     *
     * 引用块里的公式节点会把每行的 `>` 标记也圈进来（`BLOCK_QUOTE` 标记是节点的一部分），
     * 不剥掉的话 TeX 会带着 `>` 去排版，直接失败退化成源码。
     */
    private fun mathTex(rawText: String): String =
        rawText.trim()
            .removeSurrounding("$$")
            .lines()
            .joinToString("\n") { line -> line.trimStart().removePrefix(">").trimStart() }
            .trim()
}
