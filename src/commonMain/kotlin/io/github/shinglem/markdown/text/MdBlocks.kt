package io.github.shinglem.markdown.text

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

/** 段落：整宽 `Text`，行内格式由 [MdInlineText] 一次构建完成。 */
@Composable
internal fun MdParagraph(
    source: String,
    node: ASTNode,
    modifier: Modifier = Modifier,
) {
    MdInlineText(
        source = source,
        node = node,
        style = LocalMdTypography.current.paragraph,
        modifier = modifier.fillMaxWidth().padding(vertical = LocalMdPadding.current.block),
    )
}

/**
 * 标题（ATX 与 setext 都走这里）。
 *
 * 内容必须从 `ATX_CONTENT` / `SETEXT_CONTENT` 子节点取：setext 的正文挂在
 * `SETEXT_CONTENT` 下，直接遍历整个标题节点会把下划线 `---` 也当内容。
 */
@Composable
internal fun MdHeading(
    source: String,
    node: ASTNode,
    level: Int,
    modifier: Modifier = Modifier,
) {
    val content = node.children.firstOrNull {
        it.type == MarkdownTokenTypes.ATX_CONTENT || it.type == MarkdownTokenTypes.SETEXT_CONTENT
    }
    Box(
        modifier
            .fillMaxWidth()
            .padding(vertical = LocalMdPadding.current.block)
            .semantics { heading() },
    ) {
        MdInlineText(
            source = source,
            children = content?.children ?: node.children,
            style = LocalMdTypography.current.heading(level),
        )
    }
}

/** 分割线。上下留白固定 8dp：主题里的 `block` 太窄，会贴着段落。 */
@Composable
internal fun MdRule(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(LocalMdDimens.current.dividerThickness)
            .background(LocalMdColors.current.dividerColor),
    )
}

/**
 * 引用块。
 *
 * 竖条用 `drawBehind` 画在容器左缘，不靠 `fillMaxHeight()` 的子元素 ——
 * 在 `Column` 的无界高度约束下后者会退化成 `Int.MAX_VALUE`，Skia 直接拒绝绘制。
 *
 * 内容交给 [MdFlow]：引用里的嵌套引用、列表、代码块各走自己的组件，
 * 不需要把源码重新解析一遍。
 */
@Composable
internal fun MdQuote(
    source: String,
    node: ASTNode,
    modifier: Modifier = Modifier,
    depth: Int = 0,
) {
    val colors = LocalMdColors.current
    val dimens = LocalMdDimens.current
    val padding = LocalMdPadding.current
    val typography = LocalMdTypography.current
    val layoutDirection = LocalLayoutDirection.current
    // 主题里 quote 的 color 就是正文色时，拿它画竖条会和文字糊在一起
    val barColor = typography.quote.color.takeIf { it.isSpecified } ?: colors.text
    val children = remember(node) { node.children.filterNot { it.type == MarkdownTokenTypes.BLOCK_QUOTE } }

    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = padding.block)
            .drawBehind {
                drawRect(
                    color = barColor,
                    topLeft = Offset.Zero,
                    size = Size(dimens.blockQuoteThickness.toPx(), size.height),
                )
            }
            .padding(
                start = padding.blockQuote.calculateStartPadding(layoutDirection),
                end = padding.blockQuote.calculateEndPadding(layoutDirection),
                top = padding.blockQuoteText.calculateTopPadding(),
                bottom = padding.blockQuoteText.calculateBottomPadding(),
            ),
    ) {
        MdFlow(source, children, typography.quote, depth)
    }
}

/**
 * 列表（有序 / 无序 / 嵌套）。
 *
 * 每一层单独一个 `Column`：缩进按层累加，标记与正文用 `Row` 对齐，
 * 这样长条目折行时文字不会跑到标记下面。
 */
@Composable
internal fun MdList(
    source: String,
    node: ASTNode,
    ordered: Boolean,
    modifier: Modifier = Modifier,
    depth: Int = 0,
) {
    val padding = LocalMdPadding.current
    val items = remember(node) { node.children.filter { it.type == MarkdownElementTypes.LIST_ITEM } }
    val startNumber = remember(node) {
        items.firstOrNull()
            ?.children?.firstOrNull { it.type == MarkdownTokenTypes.LIST_NUMBER }
            ?.let { source.substring(it.startOffset, it.endOffset).trim().takeWhile(Char::isDigit).toIntOrNull() }
            ?: 1
    }

    Column(
        modifier
            .fillMaxWidth()
            .padding(start = padding.listIndent * depth, top = padding.list, bottom = padding.list),
    ) {
        items.forEachIndexed { index, item ->
            key(item.startOffset) {
                MdListItem(source, item, ordered, startNumber + index, depth)
            }
        }
    }
}

@Composable
private fun MdListItem(
    source: String,
    item: ASTNode,
    ordered: Boolean,
    number: Int,
    depth: Int,
) {
    val padding = LocalMdPadding.current
    val typography = LocalMdTypography.current
    val checkbox = item.children.firstOrNull { it.type == GFMTokenTypes.CHECK_BOX }
    val content = remember(item) { itemContent(item) }

    Row(modifier = Modifier.fillMaxWidth().padding(top = padding.listItemTop, bottom = padding.listItemBottom)) {
        Box(
            Modifier.widthIn(min = 20.dp).padding(end = 6.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            when {
                checkbox != null -> MdTaskCheckBox(checked = checkbox.isChecked(source))
                ordered -> Text(text = "$number.", style = typography.ordered)
                else -> Text(text = bulletFor(depth), style = typography.bullet)
            }
        }
        Column(Modifier.weight(1f)) {
            MdFlow(source, content, typography.list, depth)
        }
    }
}

/** 去掉列表标记与复选框 token：标记由 [MdListItem] 自己画，混进正文会重复。 */
private fun itemContent(item: ASTNode): List<ASTNode> {
    val children = item.children
    return children.filterIndexed { index, child ->
        when {
            child.type == MarkdownTokenTypes.LIST_BULLET -> false
            child.type == MarkdownTokenTypes.LIST_NUMBER -> false
            child.type == GFMTokenTypes.CHECK_BOX -> false
            // 复选框后面的那个空格也去掉，否则正文会多一个前导空格
            child.type == MarkdownTokenTypes.WHITE_SPACE &&
                children.getOrNull(index - 1)?.type == GFMTokenTypes.CHECK_BOX -> false

            else -> true
        }
    }
}

/** `[x]` / `[ ]` 的勾选状态。 */
private fun ASTNode.isChecked(source: String): Boolean =
    source.substring(startOffset, endOffset).contains('x', ignoreCase = true)

/**
 * 任务列表的复选框。
 *
 * 不用 `material3.Checkbox`：它自带 48dp 最小触控区，会把每一行任务撑得很高。
 * 这里画一个跟正文行高匹配的小方框，同时保留复选框语义。
 */
@Composable
private fun MdTaskCheckBox(checked: Boolean) {
    val colors = LocalMdColors.current
    val shape = RoundedCornerShape(3.dp)
    val markColor = MaterialTheme.colorScheme.surface
    Box(
        Modifier
            .size(14.dp)
            .clip(shape)
            .background(if (checked) colors.text.copy(alpha = 0.75f) else Color.Transparent)
            .border(1.dp, colors.text.copy(alpha = 0.45f), shape)
            .semantics {
                role = Role.Checkbox
                stateDescription = if (checked) "已勾选" else "未勾选"
            },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Text(text = "✓", fontSize = 9.sp, lineHeight = 9.sp, color = markColor)
        }
    }
}

private fun bulletFor(depth: Int): String = when (depth % 3) {
    1 -> "◦"
    2 -> "▪"
    else -> "•"
}

/**
 * 把一组子节点渲染成流式内容。
 *
 * 连续的行内节点合并成**一个** `Text`（否则每遇到一个 `TEXT` 就新建一个 `Text`，
 * 行内格式会被拆到不同行），块级子节点各自交给对应组件。引用体与列表项都用它。
 */
@Composable
internal fun MdFlow(
    source: String,
    children: List<ASTNode>,
    style: TextStyle,
    depth: Int = 0,
) {
    val chunks = remember(children) { chunked(children) }
    Column(Modifier.fillMaxWidth()) {
        chunks.forEach { chunk ->
            when (chunk) {
                is MdChunk.Inline -> MdInlineText(source, chunk.nodes, style, Modifier.fillMaxWidth())
                is MdChunk.Block -> MdBlockNode(source, chunk.node, depth)
            }
        }
    }
}

/** 流式内容里的一块：行内片段或一个块级节点。 */
private sealed interface MdChunk {
    class Inline(val nodes: List<ASTNode>) : MdChunk

    class Block(val node: ASTNode) : MdChunk
}

private fun chunked(children: List<ASTNode>): List<MdChunk> {
    val chunks = mutableListOf<MdChunk>()
    val inline = mutableListOf<ASTNode>()

    fun flush() {
        if (inline.isNotEmpty()) {
            chunks += MdChunk.Inline(inline.toList())
            inline.clear()
        }
    }

    children.forEach { child ->
        if (isBlockNode(child.type)) {
            flush()
            chunks += MdChunk.Block(child)
        } else {
            inline += child
        }
    }
    flush()
    return chunks
}

private fun isBlockNode(type: IElementType): Boolean = headingLevelOf(type) != null || when (type) {
    MarkdownElementTypes.PARAGRAPH,
    MarkdownElementTypes.UNORDERED_LIST,
    MarkdownElementTypes.ORDERED_LIST,
    MarkdownElementTypes.CODE_FENCE,
    MarkdownElementTypes.CODE_BLOCK,
    MarkdownElementTypes.BLOCK_QUOTE,
    MarkdownElementTypes.HTML_BLOCK,
    MarkdownTokenTypes.HORIZONTAL_RULE,
    GFMElementTypes.TABLE,
    GFMElementTypes.BLOCK_MATH,
    -> true

    else -> false
}

@Composable
private fun MdBlockNode(source: String, node: ASTNode, depth: Int) {
    val typography = LocalMdTypography.current
    val headingLevel = headingLevelOf(node.type)
    when {
        headingLevel != null -> MdHeading(source, node, headingLevel, Modifier.fillMaxWidth())
        node.type == MarkdownElementTypes.PARAGRAPH ->
            MdInlineText(source, node.children, typography.paragraph, Modifier.fillMaxWidth())

        node.type == MarkdownElementTypes.UNORDERED_LIST ->
            MdList(source, node, ordered = false, depth = depth + 1)

        node.type == MarkdownElementTypes.ORDERED_LIST ->
            MdList(source, node, ordered = true, depth = depth + 1)

        node.type == MarkdownElementTypes.BLOCK_QUOTE -> MdQuote(source, node, depth = depth + 1)

        node.type == MarkdownElementTypes.CODE_FENCE || node.type == MarkdownElementTypes.CODE_BLOCK -> {
            val (code, language) = codeOf(source, node)
            MdCodeBlock(code = code, language = language, modifier = Modifier.fillMaxWidth())
        }

        node.type == MarkdownTokenTypes.HORIZONTAL_RULE -> MdRule(Modifier.fillMaxWidth())
        // 嵌套层级里的 HTML / 表格 / 块级公式没有对应的原生组件：显示源码，不留空白
        else -> MdSource(source.substring(node.startOffset, node.endOffset), Modifier.fillMaxWidth())
    }
}

private fun headingLevelOf(type: IElementType): Int? = when (type) {
    MarkdownElementTypes.ATX_1, MarkdownElementTypes.SETEXT_1 -> 1
    MarkdownElementTypes.ATX_2, MarkdownElementTypes.SETEXT_2 -> 2
    MarkdownElementTypes.ATX_3 -> 3
    MarkdownElementTypes.ATX_4 -> 4
    MarkdownElementTypes.ATX_5 -> 5
    MarkdownElementTypes.ATX_6 -> 6
    else -> null
}

/** 代码块正文与语言：靠内容节点定位，避免 fence 行里的反引号干扰。 */
private fun codeOf(source: String, node: ASTNode): Pair<String, String?> {
    val language = node.children.firstOrNull { it.type == MarkdownTokenTypes.FENCE_LANG }
        ?.let { source.substring(it.startOffset, it.endOffset).trim() }
        ?.takeIf { it.isNotEmpty() }
    val content = node.children.filter {
        it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT || it.type == MarkdownTokenTypes.CODE_LINE
    }
    val code = if (content.isEmpty()) {
        ""
    } else {
        source.substring(content.first().startOffset, content.last().endOffset).trimEnd('\n')
    }
    return code to language
}

/** 画不出对应组件时的源码显示：可见、可复制，绝不静默丢内容。 */
@Composable
private fun MdSource(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = LocalMdTypography.current.text,
        fontFamily = FontFamily.Monospace,
        color = LocalMdColors.current.text.copy(alpha = 0.75f),
        modifier = modifier,
    )
}
