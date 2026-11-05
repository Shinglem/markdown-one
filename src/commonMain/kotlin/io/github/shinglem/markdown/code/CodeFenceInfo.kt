package io.github.shinglem.markdown.code

/**
 * 围栏语言标签里的「行高亮」范围：```` ```kotlin {1,3-5} ````。
 *
 * 与 GitHub / Shiki 的写法一致：花括号里是逗号分隔的**行号或闭区间**，行号从 1 开始。
 * 只解析、不渲染 —— 渲染在 `text/MdCodeBlock.kt`。
 */
internal data class CodeFenceInfo(
    /** 真正的语言标签（已去掉 `{…}` 部分），可能为 null（裸围栏）。 */
    val language: String?,
    /** 要高亮的行号（1 基），空集表示没有。 */
    val highlightLines: Set<Int>,
)

/**
 * 拆开 `kotlin {1,3-5}` 这种标签。
 *
 * 容错优先：花括号里写坏了就当没有高亮，但语言标签必须保住 ——
 * 否则用户会得到一块既不高亮也不着色的纯文本，比"高亮少了"更糟。
 */
internal fun parseCodeFence(label: String?): CodeFenceInfo {
    val raw = label?.trim().orEmpty()
    if (raw.isEmpty()) return CodeFenceInfo(null, emptySet())

    val braceStart = raw.indexOf('{')
    if (braceStart < 0 || !raw.trimEnd().endsWith("}")) {
        return CodeFenceInfo(raw, emptySet())
    }
    val language = raw.substring(0, braceStart).trim().takeIf { it.isNotEmpty() }
    val spec = raw.substring(braceStart + 1, raw.trimEnd().length - 1)
    return CodeFenceInfo(language, parseHighlightLines(spec))
}

/**
 * `1,3-5` → {1,3,4,5}。
 *
 * 上限 [MAX_HIGHLIGHT_LINE] 防止 `{1-999999999}` 这种输入把集合撑爆（模型偶尔会写错）。
 */
internal fun parseHighlightLines(spec: String): Set<Int> {
    if (spec.isBlank()) return emptySet()
    val out = linkedSetOf<Int>()
    spec.split(',').forEach { part ->
        val token = part.trim()
        if (token.isEmpty()) return@forEach
        val dash = token.indexOf('-', startIndex = 1)
        if (dash > 0) {
            val from = token.substring(0, dash).trim().toIntOrNull() ?: return@forEach
            val to = token.substring(dash + 1).trim().toIntOrNull() ?: return@forEach
            if (from <= 0 || to < from) return@forEach
            for (line in from..minOf(to, MAX_HIGHLIGHT_LINE)) out += line
        } else {
            val line = token.toIntOrNull() ?: return@forEach
            if (line > 0 && line <= MAX_HIGHLIGHT_LINE) out += line
        }
    }
    return out
}

/** 行号上限：超过这个值的区间直接截断（正常文档不可能有这么多行）。 */
private const val MAX_HIGHLIGHT_LINE = 100_000
