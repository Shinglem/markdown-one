package io.github.shinglem.markdown

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.mikepenz.markdown.model.MarkdownAnnotator
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.utils.getUnescapedTextInNode
import org.intellij.markdown.MarkdownTokenTypes

/** 行内内容片段：普通文本，或一段 TeX 公式。 */
internal sealed interface MathPart {
    data class Text(val text: String) : MathPart
    data class TeX(val tex: String) : MathPart
}

/** inline content 的 key 前缀：`math:<tex>`。 */
internal const val MATH_KEY_PREFIX = "math:"

/**
 * 把一段文本按 `$…$` 切成「文本 / 公式」片段。
 *
 * 几条刻意的规则（都对应真实文档里出现过的写法）：
 * - `\$` 是转义，不当作公式起始；
 * - `$$…$$` 交给块级解析（`BLOCK_MATH`），这里整段跳过；
 * - 空公式（`$$` 紧邻）与跨行内容不算行内公式，原样保留文本。
 */
internal fun splitInlineMath(text: String): List<MathPart> {
    if (!text.contains('$')) return listOf(MathPart.Text(text))
    val parts = mutableListOf<MathPart>()
    val buffer = StringBuilder()
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c == '\\' && i + 1 < text.length) {
            buffer.append(c).append(text[i + 1])
            i += 2
            continue
        }
        if (c == '$' && i + 1 < text.length && text[i + 1] == '$') {
            buffer.append("$$")
            i += 2
            continue
        }
        if (c == '$') {
            val end = text.indexOf('$', i + 1)
            if (end > i + 1) {
                val tex = text.substring(i + 1, end)
                if (tex.isNotBlank() && !tex.contains('\n')) {
                    if (buffer.isNotEmpty()) {
                        parts += MathPart.Text(buffer.toString())
                        buffer.clear()
                    }
                    parts += MathPart.TeX(tex)
                    i = end + 1
                    continue
                }
            }
        }
        buffer.append(c)
        i++
    }
    if (buffer.isNotEmpty()) parts += MathPart.Text(buffer.toString())
    return if (parts.isEmpty()) listOf(MathPart.Text(text)) else parts
}

/** 整篇文档里出现过的行内公式（用于预建 inline content 表）。 */
internal fun collectInlineTexes(markdown: String): Set<String> =
    splitInlineMath(markdown).filterIsInstance<MathPart.TeX>().mapTo(LinkedHashSet()) { it.tex }

/**
 * 行内公式的 [InlineTextContent]：按 tex 建表，渲染器由平台提供。
 *
 * desktop 用 JLaTeXMath 出位图；其它端返回空表 —— 那时 annotator 里的
 * `appendInlineContent` 找不到 key，Compose 会退回 `alternateText`（即 TeX 源码），
 * 也就是「降级成源码」而不是留空白。
 */
@Composable
internal expect fun mathInlineContents(texes: Set<String>): Map<String, InlineTextContent>

/**
 * 在默认 annotator 上拦截「含 `$…$` 的文本叶子节点」：切成文本段与公式段，公式段换成 inline content。
 *
 * 几条必须遵守的契约（看 mikepenz 的 `buildMarkdownAnnotatedString` 实现）：
 * - annotate 是**逐个子节点**调用的，`content` 永远是整段文本，节点位置靠 `node` 的 offset；
 * - 返回 `true` 表示「这个节点我处理完了」，mikepenz 会**跳过它的全部内置逻辑**；
 * - 返回 `false` 才会走内置逻辑（含 `pushStyle` + 递归子节点）。
 *
 * 因此只在「TEXT 叶子节点且真的含公式」时返回 `true`；其它情况一律交给内置逻辑 ——
 * 粗体/斜体/行内代码/链接的样式都靠外层节点递归时 `pushStyle` 兜住，在叶子上 `append`
 * 纯文本不会丢样式。
 */
internal fun mathAnnotator(base: MarkdownAnnotator): MarkdownAnnotator =
    markdownAnnotator(config = base.config) math@{ content, node ->
        if (node.type != MarkdownTokenTypes.TEXT) return@math false
        val text = node.getUnescapedTextInNode(content)
        if (!text.contains('$')) return@math false
        val parts = splitInlineMath(text)
        if (parts.none { it is MathPart.TeX }) return@math false
        parts.forEach { part ->
            when (part) {
                is MathPart.Text -> append(part.text)
                is MathPart.TeX -> appendInlineContent(MATH_KEY_PREFIX + part.tex, part.tex)
            }
        }
        true
    }

/** 组合期间缓存：annotator 与 inline content 都与文档内容绑定。 */
@Composable
internal fun rememberMathAnnotator(inlineContent: Map<String, InlineTextContent>): MarkdownAnnotator {
    val base = remember { markdownAnnotator() }
    return remember(base, inlineContent) { mathAnnotator(base) }
}
