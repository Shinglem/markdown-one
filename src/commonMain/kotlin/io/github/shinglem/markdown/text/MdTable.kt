package io.github.shinglem.markdown.text

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

/**
 * 表格。
 *
 * 列宽按可用宽度均分（`weight(1f)`）：GFM 表格没有列宽信息，均分不会出现「某一列
 * 固定 160dp 导致窄窗口被截断」的问题；超宽表格由 `BlockViewport` 的缩放 + 横向滚动兜住。
 *
 * 单元格允许折行（不设 `maxLines`）：中文内容一旦省略号截断就完全不可读了。
 * 对齐方式取自表头分隔行（`:---` / `---:` / `:---:`）。
 */
@Composable
internal fun MdTable(
    source: String,
    node: ASTNode,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMdColors.current
    val dimens = LocalMdDimens.current
    val padding = LocalMdPadding.current
    val typography = LocalMdTypography.current

    val header = remember(node) { node.children.firstOrNull { it.type == GFMElementTypes.HEADER } }
    val rows = remember(node) { node.children.filter { it.type == GFMElementTypes.ROW } }
    val columns = remember(header) { header?.children?.count { it.type == GFMTokenTypes.CELL } ?: 0 }
    val alignments = remember(node, columns) { alignmentsOf(source, node, columns) }
    val shape = RoundedCornerShape(dimens.tableCornerSize)

    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = padding.block)
            .clip(shape)
            .background(colors.tableBackground)
            .border(dimens.dividerThickness, colors.dividerColor.copy(alpha = 0.45f), shape),
    ) {
        if (header == null || columns == 0) return@Column
        MdTableRow(
            source = source,
            row = header,
            columns = columns,
            alignments = alignments,
            style = typography.table.copy(fontWeight = FontWeight.SemiBold),
        )
        rows.forEach { row ->
            MdTableDivider()
            MdTableRow(
                source = source,
                row = row,
                columns = columns,
                alignments = alignments,
                style = typography.table,
            )
        }
    }
}

@Composable
private fun MdTableRow(
    source: String,
    row: ASTNode,
    columns: Int,
    alignments: List<TextAlign>,
    style: TextStyle,
) {
    val cells = remember(row) { row.children.filter { it.type == GFMTokenTypes.CELL } }
    val cellPadding = LocalMdDimens.current.tableCellPadding
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cells.forEachIndexed { index, cell ->
            MdTableCell(
                source = source,
                cell = cell,
                style = style,
                alignment = alignments.getOrElse(index) { TextAlign.Start },
                modifier = Modifier.weight(1f).padding(cellPadding),
            )
        }
        // 行比表头短时补空单元格，否则最后一列会被其它行挤宽
        repeat((columns - cells.size).coerceAtLeast(0)) {
            Box(Modifier.weight(1f).padding(cellPadding)) {}
        }
    }
}

@Composable
private fun MdTableCell(
    source: String,
    cell: ASTNode,
    style: TextStyle,
    alignment: TextAlign,
    modifier: Modifier = Modifier,
) {
    MdInlineText(
        source = source,
        node = cell,
        style = style.copy(textAlign = alignment),
        modifier = modifier,
    )
}

@Composable
private fun MdTableDivider() {
    val colors = LocalMdColors.current
    val dimens = LocalMdDimens.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(dimens.dividerThickness)
            .background(colors.dividerColor.copy(alpha = 0.3f)),
    )
}

/**
 * 每列的对齐方式。
 *
 * 分隔行在 AST 里是**一个** `TABLE_SEPARATOR` token（整行文本），
 * 所以要按 `|` 切开后逐段判断冒号位置。
 */
private fun alignmentsOf(source: String, node: ASTNode, columns: Int): List<TextAlign> {
    val separator = node.children.firstOrNull { it.type == GFMTokenTypes.TABLE_SEPARATOR }
    val cells = separator
        ?.let { source.substring(it.startOffset, it.endOffset) }
        ?.split('|')
        ?.map { it.trim() }
        ?.dropWhile { it.isEmpty() }
        ?.dropLastWhile { it.isEmpty() }
        .orEmpty()
    return List(columns) { index ->
        val cell = cells.getOrNull(index).orEmpty()
        when {
            cell.startsWith(":") && cell.endsWith(":") -> TextAlign.Center
            cell.endsWith(":") -> TextAlign.End
            else -> TextAlign.Start
        }
    }
}
