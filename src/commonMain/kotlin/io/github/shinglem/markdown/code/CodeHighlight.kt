package io.github.shinglem.markdown.code

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.gallatinapps.syntaxmp.language.LanguageExtension
import com.gallatinapps.syntaxmp.language.LanguageId
import com.gallatinapps.syntaxmp.role.SyntaxRole
import com.gallatinapps.syntaxmp.spans.SyntaxTokenSpan
import com.gallatinapps.syntaxmp.tokenizer.LanguageTokenizer
import com.gallatinapps.syntaxmp.tokenizer.SyntaxTokenizer
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.BoldHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxThemes

/**
 * 代码块语法高亮。
 *
 * 主引擎是 **SyntaxMP**（`syntaxmp-tokenizer`，纯 Kotlin 词法器），分两块：
 * 1. 它自带的 39 种语言（含 C# / PowerShell / Markdown / INI / Properties / GraphQL /
 *    Protobuf / Makefile / SQL 方言）；
 * 2. 本文件用 `LanguageExtension` 注册的补充语言 —— bat / cmd、vue / xaml / svg、
 *    cfg / conf、jsonc / json5、scala、lua。**扩展优先于内置**，所以也能覆盖内置行为。
 *
 * 兜底顺序：SyntaxMP → `dev.snipme:highlights`（只留 coffee / perl）→ 纯文本。
 *
 * 为什么不再只靠 Highlights：它只认 20 种语言（枚举名精确小写匹配、无别名），
 * 且匹配不到时**不会**退化成无色，而是用 DEFAULT（C 系）规则去染 —— 于是 `mermaid` 的
 * `class` / `classDef` 被当成 C++ 关键字染成橙色，比不高亮更糟。
 */
internal fun highlightCode(code: String, language: String?, dark: Boolean): AnnotatedString {
    val raw = normalizeLanguage(language)
    highlightWithSyntaxMp(code, raw, dark)?.let { return it }
    val keyword = HIGHLIGHTS_ALIASES[raw]
    if (keyword != null) {
        highlightWithHighlights(code, keyword, dark)?.let { return it }
    }
    return AnnotatedString(code)
}

/** 该语言是否能着色，用于决定代码块顶栏是否显示语言名（纯文本时不出标签）。 */
internal fun isHighlightable(language: String?): Boolean {
    val raw = normalizeLanguage(language)
    return raw in SYNTAX_MP_LABELS || HIGHLIGHTS_ALIASES.containsKey(raw)
}

/** 围栏语言标签归一化：`{.kotlin}` / `Kotlin` / `kt` 都要能对上。 */
private fun normalizeLanguage(language: String?): String =
    language?.trim()?.removePrefix("{.")?.removeSuffix("}")?.lowercase().orEmpty()

/**
 * 扩展注册的语言标签。SyntaxMP 的 `languageLabels` 不一定含扩展别名，
 * 所以两者合并后再做白名单判断。
 */
private val EXTENSION_LABELS: Set<String> = setOf(
    "bat", "batch", "cmd",
    "vue", "xaml", "svg",
    "cfg", "conf",
    "jsonc", "json5",
    "scala", "lua",
)

/** SyntaxMP 的词法器：纯 Kotlin，构造一次即可复用（无全局可变状态）。 */
private val SYNTAX_MP: SyntaxTokenizer by lazy {
    SyntaxTokenizer(
        extensions = listOf(
            extension("bat", setOf("bat", "batch", "cmd"), BATCH_RULES),
            extension("vue", setOf("vue", "xaml", "svg"), MARKUP_RULES),
            extension("cfg", setOf("cfg", "conf"), INI_RULES),
            extension("jsonc", setOf("jsonc", "json5"), JSON_RULES),
            extension("scala", setOf("scala"), SCALA_RULES),
            extension("lua", setOf("lua"), LUA_RULES),
        ),
    )
}

/** SyntaxMP 认识的标签（内置 + 扩展）。 */
private val SYNTAX_MP_LABELS: Set<String> by lazy { SYNTAX_MP.languageLabels + EXTENSION_LABELS }

private fun extension(id: String, aliases: Set<String>, rules: List<CodeRule>) = LanguageExtension(
    languageId = LanguageId.fromString(id),
    aliases = aliases,
    tokenizer = ruleBasedTokenizer(rules),
)

/**
 * 把一组正则规则包成 SyntaxMP 的 [LanguageTokenizer]。
 *
 * 匹配策略与旧实现一致：从当前位置起取**最靠左**的匹配，同起点取更长的，
 * 命中后从 `end` 继续（不重叠）。
 */
private fun ruleBasedTokenizer(rules: List<CodeRule>): LanguageTokenizer = LanguageTokenizer { request ->
    val code = request.code
    val spans = mutableListOf<SyntaxTokenSpan>()
    var pos = 0
    while (pos < code.length) {
        var bestStart = Int.MAX_VALUE
        var bestEnd = -1
        var bestKind: TokenKind? = null
        for (rule in rules) {
            val match = rule.pattern.find(code, pos) ?: continue
            val group = match.groups[rule.group] ?: continue
            val start = group.range.first
            val end = group.range.last
            // 取最靠左的匹配；同起点取更长的，避免 `-` 抢走 `->` 这类情况。
            if (start < bestStart || (start == bestStart && end > bestEnd)) {
                bestStart = start
                bestEnd = end
                bestKind = rule.kind
            }
        }
        val kind = bestKind ?: break
        spans += SyntaxTokenSpan(
            start = bestStart,
            endExclusive = bestEnd + 1,
            role = kind.toRole(),
            languageId = request.languageId,
        )
        pos = bestEnd + 1
    }
    spans
}

private fun TokenKind.toRole(): SyntaxRole = when (this) {
    TokenKind.Keyword -> SyntaxRole.Keyword
    TokenKind.String -> SyntaxRole.String
    TokenKind.Comment -> SyntaxRole.Comment
    TokenKind.Number -> SyntaxRole.Number
    TokenKind.Literal -> SyntaxRole.Constant
    TokenKind.Key -> SyntaxRole.Property
    TokenKind.Tag -> SyntaxRole.Tag
    TokenKind.Attr -> SyntaxRole.Attribute
    TokenKind.Variable -> SyntaxRole.Variable
    TokenKind.Punctuation -> SyntaxRole.Punctuation
    TokenKind.Type -> SyntaxRole.Type
    TokenKind.Function -> SyntaxRole.Function
}

private fun highlightWithSyntaxMp(code: String, raw: String, dark: Boolean): AnnotatedString? {
    if (raw !in SYNTAX_MP_LABELS || code.length > MAX_CODE_LENGTH) return null
    val spans = runCatching { SYNTAX_MP.tokenize(code, raw) }.getOrNull()
    if (spans.isNullOrEmpty()) return null
    return buildAnnotatedString {
        append(code)
        spans.forEach { span ->
            val kind = syntaxMpKind(span.role.value) ?: return@forEach
            addStyle(
                SpanStyle(color = tokenColor(kind, dark)),
                span.start.coerceIn(0, code.length),
                span.endExclusive.coerceIn(0, code.length),
            )
        }
    }
}

/**
 * SyntaxMP 的 `SyntaxRole` 是点分路径（`keyword.control`、`variable.namespace`、
 * `constant.builtin.null`…），按首段归类；不认识的角色返回 null（保持正文色）。
 */
private fun syntaxMpKind(role: String): TokenKind? = when {
    role == "comment" || role.startsWith("comment.") -> TokenKind.Comment
    role == "string" || role.startsWith("string.") -> TokenKind.String
    role == "escape" -> TokenKind.Variable
    role == "number" -> TokenKind.Number
    role == "type" || role.startsWith("type.") -> TokenKind.Type
    role == "function" || role.startsWith("function.") -> TokenKind.Function
    role == "keyword" || role.startsWith("keyword.") -> TokenKind.Keyword
    role == "constant" || role.startsWith("constant.") -> TokenKind.Literal
    role == "tag" -> TokenKind.Tag
    role == "attribute" || role.startsWith("attribute.") -> TokenKind.Attr
    role == "annotation" || role.startsWith("annotation.") -> TokenKind.Attr
    role == "property" || role.startsWith("property.") -> TokenKind.Key
    role == "variable" || role.startsWith("variable.") -> TokenKind.Variable
    role.startsWith("markup.diff.addition") -> TokenKind.String
    role.startsWith("markup.diff.deletion") -> TokenKind.Key
    role.startsWith("markup.diff.") -> TokenKind.Comment
    role.startsWith("markup.link") -> TokenKind.Variable
    role.startsWith("markup.emphasis") -> TokenKind.Type
    role.startsWith("markup.list") -> TokenKind.Keyword
    else -> null
}


private fun highlightWithHighlights(code: String, language: SyntaxLanguage, dark: Boolean): AnnotatedString? =
    runCatching {
        val marks = Highlights.Builder()
            .theme(SyntaxThemes.default(darkMode = dark))
            .code(code)
            .language(language)
            .build()
            .getHighlights()
        buildAnnotatedString {
            append(code)
            marks.forEach { mark ->
                val style = when (mark) {
                    is ColorHighlight -> SpanStyle(color = Color(mark.rgb).copy(alpha = 1f))
                    is BoldHighlight -> SpanStyle(fontWeight = FontWeight.Bold)
                }
                addStyle(style, mark.location.start, mark.location.end)
            }
        }
    }.getOrNull()

internal enum class TokenKind {
    Keyword, String, Comment, Number, Literal, Key, Tag, Attr, Variable, Punctuation, Type, Function,
}

private class CodeRule(val pattern: Regex, val kind: TokenKind, val group: Int = 0)

/** 单次着色的 token 数上限与文本长度上限：超大块直接放弃着色，避免卡住 UI。 */
private const val MAX_CODE_LENGTH = 40_000

internal fun tokenColor(kind: TokenKind, dark: Boolean): Color = when (kind) {
    TokenKind.Keyword -> if (dark) Color(0xFFC678DD) else Color(0xFFA626A4)
    TokenKind.String -> if (dark) Color(0xFF98C379) else Color(0xFF50A14F)
    TokenKind.Comment -> if (dark) Color(0xFF7F848E) else Color(0xFFA0A1A7)
    TokenKind.Number -> if (dark) Color(0xFFD19A66) else Color(0xFF986801)
    TokenKind.Literal -> if (dark) Color(0xFF56B6C2) else Color(0xFF0184BC)
    TokenKind.Key -> if (dark) Color(0xFFE06C75) else Color(0xFFE45649)
    TokenKind.Tag -> if (dark) Color(0xFFE5C07B) else Color(0xFFC18401)
    TokenKind.Attr -> if (dark) Color(0xFFD19A66) else Color(0xFF986801)
    TokenKind.Variable -> if (dark) Color(0xFF56B6C2) else Color(0xFF0184BC)
    TokenKind.Punctuation -> if (dark) Color(0xFFABB2BF) else Color(0xFF383A42)
    TokenKind.Type -> if (dark) Color(0xFFE5C07B) else Color(0xFFC18401)
    TokenKind.Function -> if (dark) Color(0xFF61AFEF) else Color(0xFF4078F2)
}

/**
 * Highlights 只留 SyntaxMP 没有的语言（coffee / perl），其余一律走 SyntaxMP。
 * 保留它的意义是：这两个语言的用户极少，不值得各写一份词法器。
 */
private val HIGHLIGHTS_ALIASES: Map<String, SyntaxLanguage> = buildMap {
    fun put(language: SyntaxLanguage, vararg names: String) = names.forEach { put(it, language) }
    put(SyntaxLanguage.COFFEESCRIPT, "coffeescript", "coffee")
    put(SyntaxLanguage.PERL, "perl", "pl")
}

private val JSON_RULES = listOf(
    CodeRule(Regex("//[^\\n]*|/\\*[\\s\\S]*?\\*/"), TokenKind.Comment),
    CodeRule(Regex("\"(?:\\\\.|[^\"\\\\\\n])*\"(?=\\s*:)"), TokenKind.Key),
    CodeRule(Regex("\"(?:\\\\.|[^\"\\\\\\n])*\""), TokenKind.String),
    CodeRule(Regex("\\b-?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?\\b"), TokenKind.Number),
    CodeRule(Regex("\\b(?:true|false|null)\\b"), TokenKind.Literal),
)

private val INI_RULES = listOf(
    CodeRule(Regex("(?m)^[ \\t]*[;#][^\\n]*"), TokenKind.Comment),
    CodeRule(Regex("(?m)^[ \\t]*\\[[^\\]\\n]+\\]"), TokenKind.Tag),
    CodeRule(Regex("(?m)^[ \\t]*[\\w.-]+(?=[ \\t]*[=:])"), TokenKind.Key),
    CodeRule(Regex("\"[^\"\\n]*\"|'[^'\\n]*'"), TokenKind.String),
    CodeRule(Regex("\\b\\d+(?:\\.\\d+)?\\b"), TokenKind.Number),
)

/** vue / xaml / svg：SyntaxMP 没有对应的内置词法器，按标记语言处理。 */
private val MARKUP_RULES = listOf(
    CodeRule(Regex("<!--[\\s\\S]*?-->"), TokenKind.Comment),
    CodeRule(Regex("<\\??/?[\\w:.-]+"), TokenKind.Tag),
    CodeRule(Regex("[\\w:.-]+(?=[ \\t]*=)"), TokenKind.Attr),
    CodeRule(Regex("\"[^\"\\n]*\"|'[^'\\n]*'"), TokenKind.String),
    // Vue 插值与 XAML 绑定表达式
    CodeRule(Regex("\\{\\{[^{}]*\\}\\}|\\{[A-Za-z][^{}]*\\}"), TokenKind.Variable),
)

private val BATCH_RULES = listOf(
    CodeRule(Regex("(?mi)^[ \\t]*(?:rem\\b|::)[^\\n]*"), TokenKind.Comment),
    CodeRule(Regex("(?m)^[ \\t]*:[\\w.-]+"), TokenKind.Tag),
    CodeRule(Regex("\"[^\"\\n]*\""), TokenKind.String),
    CodeRule(Regex("%~?[\\w.*]+%|%\\d|%%~?\\w*|![\\w]+!"), TokenKind.Variable),
    CodeRule(
        Regex(
            "\\b(?i:if|else|for|in|do|goto|call|set|setlocal|endlocal|echo|exit|pushd|popd|cd|" +
                "md|rd|del|copy|xcopy|move|ren|start|timeout|pause|shift|errorlevel)\\b",
        ),
        TokenKind.Keyword,
    ),
)

/**
 * Scala：SyntaxMP 没有内置。规则只求把骨架看出来 —— 关键字 / 类型 / 注解 / 字符串 /
 * 注释 / 数字，`case class`、`object`、`val` 这些高频写法都能对上。
 */
private val SCALA_RULES = listOf(
    CodeRule(Regex("//[^\\n]*|/\\*[\\s\\S]*?\\*/"), TokenKind.Comment),
    CodeRule(Regex("\"\"\"[\\s\\S]*?\"\"\""), TokenKind.String),
    CodeRule(Regex("\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])'"), TokenKind.String),
    CodeRule(Regex("@[A-Za-z_]\\w*"), TokenKind.Attr),
    CodeRule(
        Regex(
            "\\b(?:abstract|case|catch|class|def|do|else|enum|export|extends|final|finally|for|" +
                "forSome|given|if|implicit|import|lazy|match|new|object|override|package|private|" +
                "protected|return|sealed|super|then|this|throw|trait|try|type|val|var|while|with|yield)\\b",
        ),
        TokenKind.Keyword,
    ),
    CodeRule(Regex("\\b(?:true|false|null|Nil|None|Some)\\b"), TokenKind.Literal),
    CodeRule(Regex("\\b[A-Z][A-Za-z0-9_]*\\b"), TokenKind.Type),
    CodeRule(Regex("\\b\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?\\b"), TokenKind.Number),
)

/**
 * Lua：SyntaxMP 没有内置。长注释 `--[[ ]]` 必须排在 `--` 之前，否则会被截成行注释。
 */
private val LUA_RULES = listOf(
    CodeRule(Regex("--\\[[\\s\\S]*?\\]\\]"), TokenKind.Comment),
    CodeRule(Regex("--[^\\n]*"), TokenKind.Comment),
    CodeRule(Regex("\\[\\[[\\s\\S]*?\\]\\]"), TokenKind.String),
    CodeRule(Regex("\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*'"), TokenKind.String),
    CodeRule(
        Regex(
            "\\b(?:and|break|do|else|elseif|end|for|function|goto|if|in|local|not|or|" +
                "repeat|return|then|until|while)\\b",
        ),
        TokenKind.Keyword,
    ),
    CodeRule(Regex("\\b(?:true|false|nil)\\b"), TokenKind.Literal),
    CodeRule(
        Regex("\\b(?:print|pairs|ipairs|require|type|tostring|tonumber|next|select|pcall|error|assert|setmetatable|getmetatable)\\b"),
        TokenKind.Function,
    ),
    CodeRule(Regex("\\b(?:table|string|math|io|os|coroutine|debug|utf8)\\b"), TokenKind.Type),
    CodeRule(Regex("\\b\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?\\b"), TokenKind.Number),
)
