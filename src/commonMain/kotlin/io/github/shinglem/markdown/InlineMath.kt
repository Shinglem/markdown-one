package io.github.shinglem.markdown

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.mikepenz.markdown.model.MarkdownAnnotator
import com.mikepenz.markdown.model.markdownAnnotator
import io.github.shinglem.markdown.model.MdBlock
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes

/** inline content 的 key 前缀：`math:<tex>`。 */
internal const val MATH_KEY_PREFIX = "math:"

/**
 * 文档里出现过的行内公式（用于预建 inline content 表）。
 *
 * 从解析后的 AST 收集，而不是对源码做文本切分 —— 收集与渲染必须用同一份字符串，
 * 否则 inline content 的 key 对不上会退化成显示源码。
 *
 * 注意：0.7.17 只在左界符前是空白时把 `$…$` 解析成 `INLINE_MATH`，紧贴标点/文字的
 * 情况由 `parse/InlineMathRepair` 在解析后补成同样的节点，所以这里看到的已是补过的树。
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
private fun inlineTex(source: String, node: ASTNode): String =
    source.substring(node.startOffset, node.endOffset).trim().removeSurrounding("$").trim()

/**
 * 行内公式的 [InlineTextContent]：按 tex 建表，渲染器由平台提供。
 *
 * desktop 用 JLaTeXMath 出位图；其它端返回空表 —— 那时 annotator 里的
 * `appendInlineContent` 找不到 key，Compose 会退回 `alternateText`（即 TeX 源码），
 * 也就是「降级成源码」而不是留空白。
 *
 * tex 表来自 [collectInlineTexes]，渲染侧 [mathAnnotator] 用同一个 `inlineTex()` 取 key，
 * 两条路径共用一套字符串规则。
 */
@Composable
internal expect fun mathInlineContents(texes: Set<String>): Map<String, InlineTextContent>

/**
 * 在默认 annotator 上接管 `INLINE_MATH` 节点，把公式换成 inline content。
 *
 * 只接管这一个节点类型，其余一律返回 `false` 交给 mikepenz 的内置逻辑：
 *
 * - 返回 `true` 表示「这个节点我处理完了」，mikepenz 会**跳过它的全部内置逻辑**
 *   （包括 `INLINE_MATH` 分支里把 `$…$` 原样 append 的兜底）；
 * - annotate 是**逐个子节点**调用的，`content` 永远是整段文本，节点位置靠 `node` 的 offset；
 * - **不要**再在 `MarkdownTokenTypes.TEXT` 上做 `$…$` 文本扫描：`CODE_SPAN` 的子节点
 *   同样是 `TEXT`，那样会把行内代码里的 `$x$` 也渲染成公式；而文本里残留的 `$…$`
 *   在解析阶段已由 `parse/InlineMathRepair` 补成 `INLINE_MATH`（0.7.17 只在左界符前是
 *   空白时才认，紧贴标点/文字的情况靠那个补丁）。
 */
internal fun mathAnnotator(base: MarkdownAnnotator): MarkdownAnnotator =
    markdownAnnotator(config = base.config) math@{ content, node ->
        if (node.type != GFMElementTypes.INLINE_MATH) return@math false
        val tex = inlineTex(content, node)
        if (tex.isEmpty()) return@math false
        appendInlineContent(MATH_KEY_PREFIX + tex, tex)
        true
    }

/** 组合期间缓存：annotator 与文档内容无关，只在 base 变化时重建。 */
@Composable
internal fun rememberMathAnnotator(): MarkdownAnnotator {
    val base = remember { markdownAnnotator() }
    return remember(base) { mathAnnotator(base) }
}
