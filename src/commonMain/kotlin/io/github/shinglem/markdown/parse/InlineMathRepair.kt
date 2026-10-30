package io.github.shinglem.markdown.parse

import org.intellij.markdown.IElementType
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

/**
 * 把「没被识别成 `INLINE_MATH` 的行内公式」补成 `INLINE_MATH` 节点。
 *
 * JetBrains 的 GFM 行内公式规则只在左界符 `$` 前面是空白/行首时才成立，于是
 *
 * ```
 * 当 $n \to \infty$ 时，$\bar{x} \to \mu$。
 * ```
 *
 * 里第二个公式会退化成 `DOLLAR` + `TEXT` + WHITE_SPACE + … + `DOLLAR` 一串散装节点，
 * 渲染出来就是原样的 `$\bar{x} \to \mu$`（只有第一个公式正常）。这里把相邻的一对
 * `DOLLAR` 之间的内容包成一个 `INLINE_MATH` 节点，offset 与原文保持一致 ——
 * 下游（annotator、tex 收集）都按 offset 取文本，因此不需要改动源码。
 */
internal fun repairInlineMath(node: ASTNode, source: String): ASTNode {
    if (node.children.isEmpty()) return node

    val merged = mutableListOf<ASTNode>()
    var changed = false
    val kids = node.children
    var index = 0
    while (index < kids.size) {
        val kid = kids[index]
        if (kid.type == GFMTokenTypes.DOLLAR) {
            var close = index + 1
            while (close < kids.size && kids[close].type != GFMTokenTypes.DOLLAR) close++
            if (close < kids.size) {
                val raw = source.substring(kids[index].startOffset, kids[close].endOffset)
                val inner = raw.removeSurrounding("$")
                if (inner.isNotBlank() && !inner.contains('\n')) {
                    merged += MathNode(kids.subList(index, close + 1), kids[index].startOffset, kids[close].endOffset)
                    index = close + 1
                    changed = true
                    continue
                }
            }
        }
        val rebuilt = repairInlineMath(kid, source)
        if (rebuilt !== kid) changed = true
        merged += rebuilt
        index++
    }
    if (!changed) return node
    return RebuiltNode(node.type, merged, node.startOffset, node.endOffset)
}

/** 补出来的行内公式节点：类型与 GFM 原生的一致，children 保留原散装节点。 */
private class MathNode(
    children: List<ASTNode>,
    override val startOffset: Int,
    override val endOffset: Int,
) : ASTNode {
    override val type: IElementType = GFMElementTypes.INLINE_MATH
    override val children: List<ASTNode> = children
    override var parent: ASTNode? = null
}

/**
 * 重建后的复合节点。
 *
 * `ASTNode.parent` 在接口上是只读的，没法把原节点接回新父节点；因此被重建的父节点
 * 会成为新树里其它节点的「孤儿父」。annotator 只在 `LINK_TEXT` 判断里读 parent，
 * 而替换只发生在公式节点上，不影响链接结构，所以保持原样即可。
 */
private class RebuiltNode(
    override val type: IElementType,
    children: List<ASTNode>,
    override val startOffset: Int,
    override val endOffset: Int,
) : ASTNode {
    override val children: List<ASTNode> = children
    override var parent: ASTNode? = null
}
