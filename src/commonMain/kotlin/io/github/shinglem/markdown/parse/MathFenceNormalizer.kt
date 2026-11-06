package io.github.shinglem.markdown.parse

/**
 * 把**独占一行**的 `$$ … $$` 块改写成 ` ```math ` 围栏。
 *
 * 为什么需要这一步：GFM 解析器对 `BLOCK_MATH` 的识别只认「`$$` 开始 … `$$` 结束」这一整段，
 * 一旦块内某行看起来像别的块语法（真机案例：续行以 ` + \left(…` 开头，被当成无序列表），
 * 整段就被拆成 `PARAGRAPH + LIST`，公式再也拼不回来 —— 表现为「公式显示成源码，且
 * `\sum_{i=1}` 里的 `_` 被当成强调吃掉」。围栏代码块内部不参与任何块语法，是最稳的容器。
 *
 * 约定：
 * - 只处理**行首无缩进**的 `$$`（列表 / 引用里的公式维持原状，交给 `MdBlocks` 的 `MdFlow` 兜底）；
 * - 围栏代码块（``` / ~~~）内部原样保留；
 * - 没找到结束行（流式输出的半截公式）时原样保留，不制造半个围栏。
 */
internal fun normalizeMathBlocks(source: String): String {
    if (!source.contains("$$")) return source
    val lines = source.split("\n")
    val out = ArrayList<String>(lines.size)
    var inFence = false
    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            inFence = !inFence
            out += line
            index++
            continue
        }
        if (inFence || line.trimEnd() != "$$") {
            out += line
            index++
            continue
        }
        var close = index + 1
        while (close < lines.size && lines[close].trimEnd() != "$$") close++
        if (close >= lines.size) {
            out += line
            index++
            continue
        }
        out += "```math"
        for (body in index + 1 until close) out += lines[body]
        out += "```"
        index = close + 1
    }
    return out.joinToString("\n")
}

/** 反过来的那一半：`mathFromFence` 用的语言标签（大小写不敏感）。 */
internal val MATH_FENCE_LANGUAGES = setOf("math", "latex", "tex")
