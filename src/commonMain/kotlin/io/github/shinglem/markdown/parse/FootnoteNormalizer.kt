package io.github.shinglem.markdown.parse

/**
 * 一条脚注定义。
 *
 * [number] 是渲染出来的序号（按正文**首次引用**顺序编号，未被引用的定义排在最后）；
 * [markdown] 是定义正文，允许多行（缩进续行），渲染时由 footnotes 适配器递归渲染。
 */
internal class FootnoteDefinition(
    val id: String,
    val number: Int,
    val markdown: String,
)

/** 预处理结果：[text] 是去掉定义、替换过引用的正文。 */
internal class FootnotePreprocess(
    val text: String,
    val definitions: List<FootnoteDefinition>,
)

/**
 * 脚注预处理（在解析**之前**做，与 `HtmlNormalizer` 同一层）。
 *
 * 为什么必须预处理：脚注不是 CommonMark / GFM 规范能力（GitHub 是产品特性），
 * 解析器既不产出 footnote 节点，也会把 `正文[^1]` 解析成 `SHORT_REFERENCE_LINK`、
 * 把连续的多条定义合并成一个段落。AST 层没法把它们拆开，只能在解析前动手：
 *
 * 1. 抽出 `[^id]: 内容` 定义（支持缩进续行与空行分段），并从正文里删除（留空行，避免段落被合并）；
 * 2. 正文里的 `[^id]` 改写成 `[n](#fn-id)` —— 普通行内链接，渲染层把 `#fn-` 开头的地址
 *    画成上标序号（见 `text/MdInline.kt` 的 `appendLink`）；
 * 3. 定义按**首次引用顺序**编号，未被引用的定义按出现顺序排在后面；
 * 4. 围栏代码块与行内代码里的内容一律不动（否则代码样例里的 `[^1]` 会被吃掉）。
 *
 * 没有定义时原样返回：此时 `[^1]` 保持字面量（与 GitHub 对未定义脚注的行为一致）。
 */
internal fun preprocessFootnotes(source: String): FootnotePreprocess {
    if (!source.contains("[^")) return FootnotePreprocess(source, emptyList())
    val (body, raw) = extractDefinitions(source)
    if (raw.isEmpty()) return FootnotePreprocess(source, emptyList())

    // 先扫引用（顺序决定编号），再改写正文。
    val referenced = LinkedHashSet<String>()
    scanOutsideCode(body) { chunk ->
        REFERENCE.findAll(chunk).forEach { referenced += it.groupValues[1] }
        chunk
    }
    val numbers = LinkedHashMap<String, Int>()
    referenced.forEach { id -> if (id in raw) numbers[id] = numbers.size + 1 }
    raw.keys.forEach { id -> if (id !in numbers) numbers[id] = numbers.size + 1 }

    val text = scanOutsideCode(body) { chunk ->
        REFERENCE.replace(chunk) { match ->
            val id = match.groupValues[1]
            val number = numbers[id] ?: return@replace match.value
            "[$number](#fn-$id)"
        }
    }

    val definitions = raw.map { (id, markdown) ->
        FootnoteDefinition(id = id, number = numbers[id] ?: 0, markdown = markdown)
    }.sortedBy { it.number }
    return FootnotePreprocess(text, definitions)
}

/**
 * 逐行抽出定义，返回「去掉定义的正文」与「id → 定义正文」。
 *
 * 续行判定与 CommonMark 的列表续行一致：缩进 ≥2 格的行使定义继续；
 * 单个空行之后仍有缩进行时也继续（多段脚注），否则定义在此结束。
 */
private fun extractDefinitions(source: String): Pair<String, LinkedHashMap<String, String>> {
    val lines = source.split("\n")
    val kept = ArrayList<String>(lines.size)
    val definitions = LinkedHashMap<String, String>()
    var index = 0
    var inFence = false
    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            inFence = !inFence
            kept += line
            index++
            continue
        }
        val match = if (inFence) null else DEFINITION.matchEntire(line)
        if (match == null) {
            kept += line
            index++
            continue
        }
        val id = match.groupValues[1]
        val body = StringBuilder(match.groupValues[2].trim())
        index++
        while (index < lines.size) {
            val next = lines[index]
            if (next.isBlank()) {
                val after = lines.getOrNull(index + 1)
                if (after != null && after.startsWith("  ")) {
                    body.append("\n\n")
                    index++
                    continue
                }
                break
            }
            if (next.startsWith("  ") || next.startsWith("\t")) {
                if (body.isNotEmpty() && !body.endsWith("\n")) body.append('\n')
                body.append(next.trimStart(' ', '\t'))
                index++
                continue
            }
            break
        }
        // 重复定义保留第一条（与引用式链接定义的行为一致）
        definitions.putIfAbsent(id, body.toString().trimEnd())
        kept += ""
    }
    return kept.joinToString("\n") to definitions
}

/**
 * 把正文切成「普通文本」与「代码」两类片段，只对普通文本应用 [transform]。
 *
 * 逐行处理：行首 ``` / ~~~ 翻转围栏状态；行内用反引号成对切分（与 `HtmlNormalizer`
 * 的 `rewriteLine` 同一套规则，两处都改时保持同步）。
 */
private fun scanOutsideCode(text: String, transform: (String) -> String): String {
    if (!text.contains("[^")) return text
    val out = StringBuilder(text.length)
    var inFence = false
    var lineStart = 0
    while (lineStart <= text.length) {
        val eol = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
        val line = text.substring(lineStart, eol)
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            inFence = !inFence
            out.append(line)
        } else if (inFence) {
            out.append(line)
        } else {
            var cursor = 0
            while (cursor < line.length) {
                val tick = line.indexOf('`', cursor)
                if (tick < 0) {
                    out.append(transform(line.substring(cursor)))
                    cursor = line.length
                    break
                }
                val close = line.indexOf('`', tick + 1)
                val end = if (close < 0) line.length else close + 1
                out.append(transform(line.substring(cursor, tick)))
                out.append(line.substring(tick, end))
                cursor = end
            }
        }
        if (eol >= text.length) break
        out.append('\n')
        lineStart = eol + 1
    }
    return out.toString()
}

/** `[^id]: 内容`（允许最多 3 格前导缩进）。 */
private val DEFINITION = Regex("""^ {0,3}\[\^([^\]\s]+)]:[ \t]*(.*)$""")

/** 正文里的 `[^id]` 引用（不匹配 `[^` 未闭合的情况）。 */
private val REFERENCE = Regex("""\[\^([^\]\s]+)]""")
