package io.github.shinglem.markdown

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.mikepenz.markdown.model.MarkdownAnnotator
import com.mikepenz.markdown.model.markdownAnnotator

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
 * 在默认 annotator 外面包一层：把文本节点里的 `$…$` 换成 inline content。
 *
 * 为什么要包而不是重写：默认 annotator 负责粗体/斜体/行内代码/链接的样式，
 * 重写会全部丢掉。这里只把「含 `$` 的文本节点」切成多段，逐段交给默认实现，
 * 公式段替换成 inline content —— 样式行为与原来一致。
 */
internal fun mathAnnotator(base: MarkdownAnnotator): MarkdownAnnotator =
    markdownAnnotator(config = base.config) { content, node ->
        if (!content.contains('$')) {
            base.annotate?.invoke(this, content, node) ?: true
        } else {
            var handled = false
            splitInlineMath(content).forEach { part ->
                when (part) {
                    is MathPart.Text -> {
                        if (part.text.isNotEmpty()) {
                            handled = (base.annotate?.invoke(this, part.text, node) ?: true) || handled
                        }
                    }

                    is MathPart.TeX -> {
                        appendInlineContent(MATH_KEY_PREFIX + part.tex, part.tex)
                        handled = true
                    }
                }
            }
            handled
        }
    }

/** 组合期间缓存：annotator 与 inline content 都与文档内容绑定。 */
@Composable
internal fun rememberMathAnnotator(inlineContent: Map<String, InlineTextContent>): MarkdownAnnotator {
    val base = remember { markdownAnnotator() }
    return remember(base, inlineContent) { mathAnnotator(base) }
}
