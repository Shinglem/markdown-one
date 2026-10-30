package io.github.shinglem.markdown.parse

/**
 * 把常见的内联 HTML 标签改写成等价的 Markdown 标记，交给解析器正常处理。
 *
 * 为什么要在解析前改而不是解析后改 AST：GFM 的 inline HTML 只是一个个扁平的
 * `HTML_INLINE` 叶子节点，`<b>` 与 `</b>` 之间没有父子关系，事后没法把中间的内容
 * 包进一个带样式的节点；而改写源码后重新解析，样式、行内代码、图片全都走正常路径。
 *
 * 规则刻意保守：
 * - 成对标签（`<b>`…`</b>`）才改写，单边标签原样保留 —— 正文里的 `a<b>c` 这类比较符不会被吃掉；
 * - 其它标签（`<div>`/`<details>`/`<span>`…）不在这里处理，交给块级适配器或源码兜底；
 * - ` ``` ` / `~~~` 围栏代码块与行内代码里的内容一律不动；
 * - `<br>` 变成硬换行（行尾两个空格 + 换行），`<img>` 变成 `![alt](src)`，`<a href>` 变成链接；
 * - `<table>` 这类「用 HTML 排版」的表格转成 Markdown 表格，借 Markdown 表格渲染器画出对齐与边框。
 */
internal fun normalizeInlineHtml(source: String): String {
    if (!source.contains('<')) return source
    return rewriteLines(rewriteTables(source))
}

/** 先把 `<table>…</table>` 换成 Markdown 表格（跨行，必须在逐行处理之前做）。 */
private fun rewriteTables(source: String): String {
    if (!HTML_TABLE.containsMatchIn(source)) return source
    val fences = fenceRanges(source)
    return HTML_TABLE.replace(source) { match ->
        if (fences.any { match.range.first in it || match.range.last in it }) {
            match.value
        } else {
            toMarkdownTable(match)
        }
    }
}

/** 围栏代码块占据的字符区间（这些区间里的内容不参与 HTML 改写）。 */
private fun fenceRanges(source: String): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    var open = -1
    var lineStart = 0
    while (lineStart < source.length) {
        val eol = source.indexOf('\n', lineStart).let { if (it < 0) source.length else it }
        val trimmed = source.substring(lineStart, eol).trimStart()
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            if (open < 0) {
                open = lineStart
            } else {
                ranges += open..eol
                open = -1
            }
        }
        lineStart = eol + 1
    }
    if (open >= 0) ranges += open..source.length - 1
    return ranges
}

private fun toMarkdownTable(match: MatchResult): String {
    val rows = TABLE_ROW.findAll(match.groupValues[1])
        .map { row -> TABLE_CELL.findAll(row.groupValues[1]).map { cell(it.groupValues[1]) }.toList() }
        .filter { it.isNotEmpty() }
        .toList()
    if (rows.isEmpty()) return match.value

    val width = rows.maxOf { it.size }
    fun line(cells: List<String>): String =
        "| " + (cells + List(width - cells.size) { "" }).joinToString(" | ") + " |"

    return buildString {
        append("\n\n")
        append(line(rows.first()))
        append('\n')
        append("| " + List(width) { "---" }.joinToString(" | ") + " |")
        rows.drop(1).forEach {
            append('\n')
            append(line(it))
        }
        append("\n\n")
    }
}

/** 单元格内容：允许里面还有行内标签，`|` 与换行必须转义，否则会破坏表格结构。 */
private fun cell(html: String): String =
    rewriteTags(html).replace('\n', ' ').replace("|", "\\|").trim()

/** 逐行改写：行内代码（反引号内）原样保留。 */
private fun rewriteLines(source: String): String {
    val out = StringBuilder(source.length)
    var inFence = false
    var index = 0
    while (index <= source.length) {
        val eol = source.indexOf('\n', index)
        val end = if (eol < 0) source.length else eol
        val line = source.substring(index, end)
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            inFence = !inFence
            out.append(line)
        } else if (inFence) {
            out.append(line)
        } else {
            out.append(rewriteLine(line))
        }
        if (eol < 0) break
        out.append('\n')
        index = eol + 1
    }
    return out.toString()
}

/** 逐段改写一行：行内代码（反引号内）原样保留。 */
private fun rewriteLine(line: String): String {
    if (!line.contains('<')) return line
    val builder = StringBuilder(line.length)
    var i = 0
    while (i < line.length) {
        val tick = line.indexOf('`', i)
        if (tick < 0) {
            builder.append(rewriteTags(line.substring(i)))
            break
        }
        val close = line.indexOf('`', tick + 1)
        builder.append(rewriteTags(line.substring(i, if (close < 0) line.length else close + 1)))
        if (close < 0) break
        i = close + 1
    }
    return builder.toString()
}

private fun rewriteTags(text: String): String {
    if (!text.contains('<')) return text
    var result = text
    result = IMAGE.replace(result) { match ->
        val attrs = match.groupValues[1]
        val src = attr(attrs, "src")
        if (src.isBlank()) match.value else "![${attr(attrs, "alt")}]($src)"
    }
    result = LINK.replace(result) { match ->
        val href = attr(match.groupValues[1], "href")
        if (href.isBlank()) match.value else "[${match.groupValues[2]}]($href)"
    }
    result = BREAK.replace(result, "  \n")
    for ((regex, marker) in TAG_MARKERS) {
        result = regex.replace(result) { match -> marker + match.groupValues[2] + marker }
    }
    return result
}

private fun attr(attrs: String, name: String): String =
    Regex("""\b$name\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        .find(attrs)?.groupValues?.get(1)?.trim().orEmpty()

private val BREAK = Regex("""<br\s*/?\s*>""", RegexOption.IGNORE_CASE)
private val IMAGE = Regex("""<img\b([^>]*)>""", RegexOption.IGNORE_CASE)
private val LINK = Regex(
    """<a\b([^>]*)>(.*?)</a\s*>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val HTML_TABLE = Regex(
    """<table\b[^>]*>(.*?)</table\s*>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val TABLE_ROW = Regex(
    """<tr\b[^>]*>(.*?)</tr\s*>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val TABLE_CELL = Regex(
    """<t[hd]\b[^>]*>(.*?)</t[hd]\s*>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

private val PAIRED = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)

/**
 * 成对标签 → Markdown 标记。**必须成对**才替换：正文里 `a<b>c` 这样的比较符
 * 没有闭合标签，保持原样比吃掉更安全。
 */
private val TAG_MARKERS: List<Pair<Regex, String>> = listOf(
    Regex("""<(b|strong)\s*>(.*?)</\1\s*>""", PAIRED) to "**",
    Regex("""<(i|em)\s*>(.*?)</\1\s*>""", PAIRED) to "*",
    Regex("""<(del|s|strike)\s*>(.*?)</\1\s*>""", PAIRED) to "~~",
    Regex("""<(code)\s*>(.*?)</\1\s*>""", PAIRED) to "`",
)

