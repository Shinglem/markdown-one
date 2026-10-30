package io.github.shinglem.markdown.parse

/**
 * 补齐流式回复里未闭合的 ``` 栅栏。
 *
 * 只统计**行首**（允许前导空白）的栅栏，行内的反引号不计入。
 * 不补齐的话，最后一个 ``` 到达前整段会被解析成普通段落，看起来像「代码块没渲染」。
 */
internal fun balanceCodeFences(markdown: String): String {
    var fences = 0
    var index = 0
    while (true) {
        val at = markdown.indexOf("```", index)
        if (at < 0) break
        var lineStart = at
        while (lineStart > 0 && markdown[lineStart - 1] != '\n') lineStart--
        if (markdown.substring(lineStart, at).isBlank()) fences++
        index = at + 3
    }
    return if (fences % 2 == 1) {
        if (markdown.endsWith("\n")) "$markdown```" else "$markdown\n```"
    } else {
        markdown
    }
}
