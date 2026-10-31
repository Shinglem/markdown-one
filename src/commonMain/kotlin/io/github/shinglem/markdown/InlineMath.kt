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
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

/** inline content 的 key 前缀：`math:<tex>`。 */
internal const val MATH_KEY_PREFIX = "math:"

/**
 * 文档里出现过的行内公式（用于预建 inline content 表）。
 *
 * 从解析后的 AST 收集，而不是对源码做文本切分 —— 收集与渲染必须用同一份字符串，
 * 否则 inline content 的 key 对不上会退化成显示源码。
 *
 * 只收集真正的 `INLINE_MATH` 节点：0.7.17 的 `canOpenMath` 要求 `$` 前是空白或行首，
 * 紧贴标点/汉字的 `$…$` 会保留成 `DOLLAR` token（渲染时按源码显示，不在这里收集）。
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
 * 只接管两类节点，其余一律返回 `false` 交给 mikepenz 的内置逻辑：
 *
 * - `INLINE_MATH`：返回 `true` 会跳过它的全部内置逻辑（含把 `$…$` 原样 append 的兜底）；
 * - `GFMTokenTypes.DOLLAR`：**未成对的 `$`**（例如紧贴文字的 `时，$x$`）在 0.7.17 里
 *   不会成为 `INLINE_MATH`，而 mikepenz 的 `else` 分支会静默丢掉它 —— 不接管的话
 *   `时，$x$` 会渲染成 `时，x`（少字符）。这里原样输出，保持「显示源码」。
 *
 * **不要**在 `MarkdownTokenTypes.TEXT` 上做 `$…$` 文本扫描：`CODE_SPAN` 的子节点
 * 同样是 `TEXT`，那样会把行内代码里的 `$x$` 也渲染成公式。
 *
 * 识别口径与上游一致：**`$` 前必须是空白或行首**（0.7.17 的 `canOpenMath`），
 * 紧贴中文标点/汉字的 `$…$` 不渲染公式，只显示源码 —— 这是刻意不补偿的。
 */
internal fun mathAnnotator(base: MarkdownAnnotator): MarkdownAnnotator =
    markdownAnnotator(config = base.config) math@{ content, node ->
        when (node.type) {
            GFMElementTypes.INLINE_MATH -> {
                val tex = inlineTex(content, node)
                if (tex.isEmpty()) return@math false
                appendInlineContent(MATH_KEY_PREFIX + tex, tex)
                true
            }

            GFMTokenTypes.DOLLAR -> {
                append(content.substring(node.startOffset, node.endOffset))
                true
            }

            else -> false
        }
    }

/** 组合期间缓存：annotator 与文档内容无关，只在 base 变化时重建。 */
@Composable
internal fun rememberMathAnnotator(): MarkdownAnnotator {
    val base = remember { markdownAnnotator() }
    return remember(base) { mathAnnotator(base) }
}
