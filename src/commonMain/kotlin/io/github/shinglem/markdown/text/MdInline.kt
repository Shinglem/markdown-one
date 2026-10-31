package io.github.shinglem.markdown.text

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import io.github.shinglem.markdown.MATH_KEY_PREFIX
import io.github.shinglem.markdown.inlineTex
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.html.entities.EntityConverter

/** 行内图片 inline content 的 key 前缀。 */
internal const val IMAGE_KEY_PREFIX = "md_image:"

/**
 * 行内构建需要的样式。
 *
 * 单独抽成对象而不是读 CompositionLocal：构建函数是纯函数，可以 `remember` 缓存，
 * 也就能在测试里直接断言输出。
 */
internal data class MdInlineStyles(
    val link: TextLinkStyles,
    val code: SpanStyle,
    val highlight: SpanStyle,
    val references: Map<String, String>,
    val linkListener: LinkInteractionListener?,
)

/** 一张行内图片：inline content 的 key、地址与替代文本。 */
internal class MdImageRef(
    val key: String,
    val url: String,
    val alt: String?,
)

/** 行内构建结果：文本 + 需要配套 inline content 的图片。 */
internal class MdInline(
    val text: AnnotatedString,
    val images: List<MdImageRef>,
)

/** 从一个块节点构建行内文本。 */
internal fun buildMdInline(source: String, node: ASTNode, styles: MdInlineStyles): MdInline =
    buildMdInline(source, node.children, styles)

/** 从一组兄弟节点构建行内文本（列表项 / 引用里会把连续的行内节点拼成一段）。 */
internal fun buildMdInline(source: String, children: List<ASTNode>, styles: MdInlineStyles): MdInline {
    val walker = InlineWalker(source, styles)
    walker.appendChildren(children)
    return MdInline(walker.text(), walker.images)
}

/**
 * 收集整篇文档里的链接定义（`[label]: url`）。
 *
 * 引用式链接 `[文字][label]` 的地址只出现在定义里，必须全文扫一遍才能解析。
 * 存在这张表里一起交代给构建器，比在渲染时回溯整棵树便宜。
 */
internal fun collectLinkDefinitions(source: String, roots: List<ASTNode>): Map<String, String> {
    val definitions = LinkedHashMap<String, String>()
    fun visit(node: ASTNode) {
        if (node.type == MarkdownElementTypes.LINK_DEFINITION) {
            val label = node.findChildOfType(MarkdownElementTypes.LINK_LABEL)
            val destination = node.findChildOfType(MarkdownElementTypes.LINK_DESTINATION)
            if (label != null && destination != null) {
                // GFM 的 label 大小写不敏感
                definitions[unescape(source, label).lowercase()] = unescape(source, destination)
            }
        }
        node.children.forEach(::visit)
    }
    roots.forEach(::visit)
    return definitions
}

/**
 * 走一遍 AST 生成 `AnnotatedString`。
 *
 * 与「先拼字符串再打补丁」的做法相比，一次走完的好处是不会把行内代码里的 `$x$`
 * 当成公式、也不会把引用式链接的方括号当普通字符输出。
 */
private class InlineWalker(
    private val source: String,
    private val styles: MdInlineStyles,
) {
    private val builder = AnnotatedString.Builder()
    val images: MutableList<MdImageRef> = mutableListOf()

    /** 行内代码内部不处理转义（`\*` 在代码里就是两个字符）。 */
    private var codeDepth = 0

    fun text(): AnnotatedString = builder.toAnnotatedString()

    fun appendChildren(children: List<ASTNode>) {
        var index = 0
        while (index < children.size) {
            val child = children[index]
            when {
                // 硬换行后面必跟一个 EOL，跳过它，否则会多出一个空格
                child.type == MarkdownTokenTypes.HARD_LINE_BREAK -> {
                    if (builder.length > 0) builder.append('\n')
                    if (children.getOrNull(index + 1)?.type == MarkdownTokenTypes.EOL) index++
                }

                // `<a@b.c>` 解析成 LT / EMAIL_AUTOLINK / GT 三个兄弟节点，尖括号不该出现在结果里
                child.type == MarkdownTokenTypes.LT &&
                    children.getOrNull(index + 1)?.type == MarkdownTokenTypes.EMAIL_AUTOLINK -> Unit

                child.type == MarkdownTokenTypes.GT &&
                    children.getOrNull(index - 1)?.type == MarkdownTokenTypes.EMAIL_AUTOLINK -> Unit

                else -> appendNode(child)
            }
            index++
        }
    }

    private fun appendNode(node: ASTNode) {
        when (node.type) {
            MarkdownElementTypes.PARAGRAPH -> appendChildren(node.children)

            MarkdownElementTypes.EMPH ->
                styled(SpanStyle(fontStyle = FontStyle.Italic), node) { it.type == MarkdownTokenTypes.EMPH }

            MarkdownElementTypes.STRONG ->
                styled(SpanStyle(fontWeight = FontWeight.Bold), node) { it.type == MarkdownTokenTypes.EMPH }

            GFMElementTypes.STRIKETHROUGH ->
                styled(SpanStyle(textDecoration = TextDecoration.LineThrough), node) { it.type == GFMTokenTypes.TILDE }

            GFMElementTypes.HIGHLIGHT -> styled(styles.highlight, node) { it.type == GFMTokenTypes.EQUALS }

            MarkdownElementTypes.CODE_SPAN -> appendCodeSpan(node)
            MarkdownElementTypes.INLINE_LINK -> appendLink(node)
            MarkdownElementTypes.AUTOLINK -> appendAutolink(node)
            MarkdownElementTypes.FULL_REFERENCE_LINK,
            MarkdownElementTypes.SHORT_REFERENCE_LINK,
            -> appendReference(node)

            MarkdownElementTypes.IMAGE -> appendImage(node)

            // 行内公式交给 inline content；找不到内容时 Compose 会退回 alternateText（TeX 源码）
            GFMElementTypes.INLINE_MATH -> appendInlineMath(node)
            GFMElementTypes.BLOCK_MATH -> builder.append(raw(node))

            MarkdownTokenTypes.TEXT -> builder.append(if (codeDepth > 0) raw(node) else unescape(source, node))
            MarkdownTokenTypes.WHITE_SPACE -> if (builder.length > 0) builder.append(' ')
            // 软换行（段内回车）按一个空格处理，与浏览器一致
            MarkdownTokenTypes.EOL -> if (builder.length > 0) builder.append(' ')
            MarkdownTokenTypes.HARD_LINE_BREAK -> if (builder.length > 0) builder.append('\n')

            MarkdownTokenTypes.SINGLE_QUOTE -> builder.append('\'')
            MarkdownTokenTypes.DOUBLE_QUOTE -> builder.append('"')
            MarkdownTokenTypes.LPAREN -> builder.append('(')
            MarkdownTokenTypes.RPAREN -> builder.append(')')
            MarkdownTokenTypes.LBRACKET -> builder.append('[')
            MarkdownTokenTypes.RBRACKET -> builder.append(']')
            MarkdownTokenTypes.LT -> builder.append('<')
            MarkdownTokenTypes.GT -> builder.append('>')
            MarkdownTokenTypes.COLON -> builder.append(':')
            MarkdownTokenTypes.EXCLAMATION_MARK -> builder.append('!')
            MarkdownTokenTypes.BACKTICK, MarkdownTokenTypes.ESCAPED_BACKTICKS -> builder.append(raw(node))
            MarkdownTokenTypes.BAD_CHARACTER -> builder.append(raw(node))
            MarkdownTokenTypes.EMAIL_AUTOLINK -> appendEmail(raw(node))

            GFMTokenTypes.GFM_AUTOLINK -> appendAutolinkText(raw(node))
            // 未成对的 `$`（0.7.17 要求 `$` 前是空白才识别成公式）必须原样输出，
            // 否则 `时，$x$` 会渲染成 `时，x`。
            GFMTokenTypes.DOLLAR -> builder.append(raw(node))
            GFMTokenTypes.EQUALS -> builder.append('=')
            GFMTokenTypes.TILDE -> builder.append('~')

            else -> Unit
        }
    }

    private inline fun styled(style: SpanStyle, node: ASTNode, skip: (ASTNode) -> Boolean) {
        builder.pushStyle(style)
        appendChildren(node.children.filterNot(skip))
        builder.pop()
    }

    /**
     * 行内代码。
     *
     * 两侧各补一个空格是刻意的：背景色贴着字符会显得拥挤，而反引号本身会被过滤掉，
     * 所以这两个空格就是「内边距」。
     */
    private fun appendCodeSpan(node: ASTNode) {
        builder.pushStyle(styles.code)
        builder.append(' ')
        codeDepth++
        appendChildren(node.children.filterNot { it.type == MarkdownTokenTypes.BACKTICK })
        codeDepth--
        builder.append(' ')
        builder.pop()
    }

    private fun appendLink(node: ASTNode) {
        val textNode = node.findChildOfType(MarkdownElementTypes.LINK_TEXT)
        val inner = textNode?.children?.filterNot { it.type.isBracket() }
        if (inner == null) {
            builder.append(raw(node))
            return
        }
        val destination = node.findChildRecursive(MarkdownElementTypes.LINK_DESTINATION)?.let { unescape(source, it) }
        if (destination.isNullOrBlank()) {
            appendChildren(inner)
            return
        }
        withLink(destination) { appendChildren(inner) }
    }

    private fun appendAutolink(node: ASTNode) {
        val target = node.children.firstOrNull {
            it.type == MarkdownTokenTypes.AUTOLINK || it.type == MarkdownTokenTypes.EMAIL_AUTOLINK
        } ?: node
        val text = raw(target)
        if (target.type == MarkdownTokenTypes.EMAIL_AUTOLINK) {
            appendEmail(text)
        } else {
            appendAutolinkText(text)
        }
    }

    /** 引用式链接 `[文字][label]` / `[label]`。定义缺失时原样输出，不丢字符。 */
    private fun appendReference(node: ASTNode) {
        val labelNode = node.findChildOfType(MarkdownElementTypes.LINK_LABEL)
        val label = labelNode?.let { unescape(source, it) }
        val url = label?.let { styles.references[it.lowercase()] }
        val inner = (node.findChildOfType(MarkdownElementTypes.LINK_TEXT)?.children ?: labelNode?.children)
            ?.filterNot { it.type.isBracket() }
        if (url == null || inner.isNullOrEmpty()) {
            builder.append(raw(node))
            return
        }
        withLink(url) { appendChildren(inner) }
    }

    private fun appendImage(node: ASTNode) {
        val url = node.findChildRecursive(MarkdownElementTypes.LINK_DESTINATION)?.let { unescape(source, it) }
            ?: node.findChildRecursive(MarkdownElementTypes.LINK_LABEL)
                ?.let { label -> styles.references[unescape(source, label).lowercase()] }
        if (url.isNullOrBlank()) {
            builder.append(raw(node))
            return
        }
        val alt = node.findChildRecursive(MarkdownElementTypes.LINK_TEXT)
            ?.let { unescape(source, it).trim().removeSurrounding("[", "]").trim() }
            ?.takeIf { it.isNotEmpty() }
        val ref = MdImageRef(IMAGE_KEY_PREFIX + url, url, alt)
        images += ref
        builder.appendInlineContent(ref.key, alt ?: url)
    }

    private fun appendInlineMath(node: ASTNode) {
        val tex = inlineTex(source, node)
        if (tex.isEmpty()) {
            builder.append(raw(node))
            return
        }
        builder.appendInlineContent(MATH_KEY_PREFIX + tex, tex)
    }

    private fun appendEmail(address: String) {
        withLink("mailto:$address") { builder.append(address) }
    }

    private fun appendAutolinkText(url: String) {
        // `www.example.com` 这种裸地址需要补协议，否则点击后打不开
        val href = if (url.startsWith("www.", ignoreCase = true)) "https://$url" else url
        withLink(href) { builder.append(url) }
    }

    private inline fun withLink(url: String, block: AnnotatedString.Builder.() -> Unit) {
        builder.withLink(
            LinkAnnotation.Url(url = url, styles = styles.link, linkInteractionListener = styles.linkListener),
            block,
        )
    }

    private fun raw(node: ASTNode): String = source.substring(node.startOffset, node.endOffset)
}

private fun IElementType.isBracket(): Boolean =
    this == MarkdownTokenTypes.LBRACKET || this == MarkdownTokenTypes.RBRACKET

/** 链接文字：反转义 `\*` 之类的转义序列，保留实体原样。 */
private fun unescape(source: String, node: ASTNode): String =
    EntityConverter.replaceEntities(
        source.substring(node.startOffset, node.endOffset),
        processEntities = false,
        processEscapes = true,
    )

private fun ASTNode.findChildRecursive(type: IElementType): ASTNode? {
    children.forEach { child ->
        if (child.type == type) return child
        child.findChildRecursive(type)?.let { return it }
    }
    return null
}
