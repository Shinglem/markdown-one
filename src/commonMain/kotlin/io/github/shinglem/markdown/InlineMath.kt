package io.github.shinglem.markdown

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import io.github.shinglem.markdown.model.MdBlock
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes

/** inline content 的 key 前缀：`math:<tex>`。 */
internal const val MATH_KEY_PREFIX = "math:"

/**
 * 文档里出现过的行内公式（用于预建 inline content 表）。
 *
 * 从解析后的 AST 收集，而不是对源码做文本切分 —— 收集与渲染必须用同一份字符串，
 * 否则 inline content 的 key 对不上会退化成显示源码。行内构建器
 * （`text/MdInline.kt`）渲染时用同一个 [inlineTex] 取 key，两条路径共用一套规则。
 *
 * 只收集真正的 `INLINE_MATH` 节点：0.7.17 的 `canOpenMath` 要求 `$` 前是空白或行首，
 * 紧贴标点/汉字的 `$…$` 会保留成 `DOLLAR` token（渲染时按源码显示，不在这里收集）。
 *
 * 表格单元格、引用、列表项里的公式也在收集范围内：整棵块子树都会被访问，
 * 而它们渲染时用的是同一张表。
 */
internal fun collectInlineTexes(blocks: List<MdBlock>, source: String): Set<String> {
    val texes = LinkedHashSet<String>()
    fun visit(node: ASTNode) {
        if (node.type == GFMElementTypes.INLINE_MATH) {
            inlineTex(source, node).takeIf { it.isNotEmpty() }?.let(texes::add)
        }
        node.children.forEach(::visit)
    }
    blocks.forEach { visit(it.node) }
    return texes
}

/** `$x$` → `x`。行内 tex 的唯一来源：收集与渲染都必须经过这里。 */
internal fun inlineTex(source: String, node: ASTNode): String =
    source.substring(node.startOffset, node.endOffset).trim().removeSurrounding("$").trim()

/**
 * 行内公式的 [InlineTextContent]：按 tex 建表，渲染器由平台提供。
 *
 * desktop 用 `latex-renderer` 直接产出 inline content；其它端返回空表 —— 那时
 * 文本里的 `appendInlineContent` 找不到 key，Compose 会退回 `alternateText`（即 TeX 源码），
 * 也就是「降级成源码」而不是留空白。
 *
 * 颜色与字号显式传入：调用方在铺主题的 CompositionLocal 之前就要算这张表。
 */
@Composable
internal expect fun mathInlineContents(
    texes: Set<String>,
    color: Color,
    fontSize: TextUnit,
): Map<String, InlineTextContent>
