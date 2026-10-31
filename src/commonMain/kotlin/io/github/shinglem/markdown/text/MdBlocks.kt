package io.github.shinglem.markdown.text

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode

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
