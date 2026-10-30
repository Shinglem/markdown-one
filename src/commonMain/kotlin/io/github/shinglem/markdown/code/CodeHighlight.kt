package io.github.shinglem.markdown.code

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.BoldHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxThemes

/**
 * 代码块语法高亮。
 *
 * `dev.snipme:highlights` 只认 20 种语言，且 `getByName` 是枚举名的精确小写匹配（无别名），
 * 匹配不到时它**不会**退化成无色，而是用 DEFAULT（C 系）规则去染 —— 于是 `mermaid` 的
 * `class` / `classDef` 被当成 C++ 关键字染成橙色，比不高亮更糟。
 *
 * 所以这里分三层：
 * 1. 别名归一化（`bash`/`sh` → SHELL、`kt` → KOTLIN、`ts` → TYPESCRIPT…）交给 Highlights；
 * 2. Highlights 覆盖不到的高频语言（json / yaml / html / sql / powershell / bat / diff …）
 *    用下面的轻量词法器按正则着色；
 * 3. 都不认识就不上色，保持纯文本。
 */
internal fun highlightCode(code: String, language: String?, dark: Boolean): AnnotatedString {
    val raw = language?.trim()?.removePrefix("{.")?.removeSuffix("}")?.lowercase().orEmpty()
    val keyword = HIGHLIGHTS_ALIASES[raw]
    if (keyword != null) {
        highlightWithHighlights(code, keyword, dark)?.let { return it }
    }
    val rules = RULE_SETS[raw]
    if (rules != null) return highlightWithRules(code, rules, dark)
    return AnnotatedString(code)
}

/** 该语言是否能着色，用于决定代码块顶栏是否显示语言名（纯文本时不出标签）。 */
internal fun isHighlightable(language: String?): Boolean {
    val raw = language?.trim()?.removePrefix("{.")?.removeSuffix("}")?.lowercase().orEmpty()
    return HIGHLIGHTS_ALIASES.containsKey(raw) || RULE_SETS.containsKey(raw)
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

internal enum class TokenKind { Keyword, String, Comment, Number, Literal, Key, Tag, Attr, Variable, Punctuation }

private class CodeRule(val pattern: Regex, val kind: TokenKind, val group: Int = 0)

/** 单次着色的 token 数上限与文本长度上限：超大块直接放弃着色，避免卡住 UI。 */
private const val MAX_CODE_LENGTH = 40_000

private fun highlightWithRules(code: String, rules: List<CodeRule>, dark: Boolean): AnnotatedString {
    if (code.length > MAX_CODE_LENGTH) return AnnotatedString(code)
    val spans = mutableListOf<TokenSpan>()
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
        spans += TokenSpan(bestStart, bestEnd + 1, kind)
        pos = bestEnd + 1
    }
    return buildAnnotatedString {
        append(code)
        spans.forEach { span ->
            addStyle(SpanStyle(color = tokenColor(span.kind, dark)), span.start, span.end.coerceAtMost(code.length))
        }
    }
}

private class TokenSpan(val start: Int, val end: Int, val kind: TokenKind)

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
}

private val HIGHLIGHTS_ALIASES: Map<String, SyntaxLanguage> = buildMap {
    fun put(language: SyntaxLanguage, vararg names: String) = names.forEach { put(it, language) }
    put(SyntaxLanguage.C, "c", "h")
    put(SyntaxLanguage.CPP, "cpp", "c++", "cxx", "hpp", "hxx", "cc")
    put(SyntaxLanguage.DART, "dart")
    put(SyntaxLanguage.JAVA, "java")
    put(SyntaxLanguage.KOTLIN, "kotlin", "kt", "kts")
    put(SyntaxLanguage.RUST, "rust", "rs")
    put(SyntaxLanguage.CSHARP, "csharp", "cs", "c#")
    put(SyntaxLanguage.COFFEESCRIPT, "coffeescript", "coffee")
    put(SyntaxLanguage.JAVASCRIPT, "javascript", "js", "jsx", "mjs", "cjs", "node")
    put(SyntaxLanguage.PERL, "perl", "pl")
    put(SyntaxLanguage.PYTHON, "python", "py", "python3", "py3")
    put(SyntaxLanguage.RUBY, "ruby", "rb")
    put(SyntaxLanguage.SHELL, "shell", "sh", "bash", "zsh", "fish", "console", "shellsession", "terminal")
    put(SyntaxLanguage.SWIFT, "swift")
    put(SyntaxLanguage.TYPESCRIPT, "typescript", "ts", "tsx", "mts")
    put(SyntaxLanguage.GO, "go", "golang")
    put(SyntaxLanguage.PHP, "php")
}

private val JSON_RULES = listOf(
    CodeRule(Regex("//[^\\n]*|/\\*[\\s\\S]*?\\*/"), TokenKind.Comment),
    CodeRule(Regex("\"(?:\\\\.|[^\"\\\\\\n])*\"(?=\\s*:)"), TokenKind.Key),
    CodeRule(Regex("\"(?:\\\\.|[^\"\\\\\\n])*\""), TokenKind.String),
    CodeRule(Regex("\\b-?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?\\b"), TokenKind.Number),
    CodeRule(Regex("\\b(?:true|false|null)\\b"), TokenKind.Literal),
)

private val YAML_RULES = listOf(
    CodeRule(Regex("#[^\\n]*"), TokenKind.Comment),
    CodeRule(Regex("\"(?:\\\\.|[^\"\\\\\\n])*\"|'[^'\\n]*'"), TokenKind.String),
    CodeRule(Regex("(?m)^[ \\t]*(?:-[ \\t]+)?[\\w./-]+(?=[ \\t]*:(?:[ \\t]|$))"), TokenKind.Key),
    CodeRule(Regex("(?m)^[ \\t]*-(?=[ \\t])"), TokenKind.Punctuation),
    CodeRule(Regex("\\b(?:true|false|null|yes|no|on|off)\\b", RegexOption.IGNORE_CASE), TokenKind.Literal),
    CodeRule(Regex("\\b\\d+(?:\\.\\d+)?\\b"), TokenKind.Number),
)

private val INI_RULES = listOf(
    CodeRule(Regex("(?m)^[ \\t]*[;#][^\\n]*"), TokenKind.Comment),
    CodeRule(Regex("(?m)^[ \\t]*\\[[^\\]\\n]+\\]"), TokenKind.Tag),
    CodeRule(Regex("(?m)^[ \\t]*[\\w.-]+(?=[ \\t]*[=:])"), TokenKind.Key),
    CodeRule(Regex("\"[^\"\\n]*\"|'[^'\\n]*'"), TokenKind.String),
    CodeRule(Regex("\\b\\d+(?:\\.\\d+)?\\b"), TokenKind.Number),
)

private val MARKUP_RULES = listOf(
    CodeRule(Regex("<!--[\\s\\S]*?-->"), TokenKind.Comment),
    CodeRule(Regex("<\\??/?[\\w:.-]+"), TokenKind.Tag),
    CodeRule(Regex("[\\w:-]+(?=[ \\t]*=)"), TokenKind.Attr),
    CodeRule(Regex("\"[^\"\\n]*\"|'[^'\\n]*'"), TokenKind.String),
)

private val SQL_RULES = listOf(
    CodeRule(Regex("--[^\\n]*|/\\*[\\s\\S]*?\\*/"), TokenKind.Comment),
    CodeRule(Regex("'[^'\\n]*'"), TokenKind.String),
    CodeRule(
        Regex(
            "\\b(?i:select|from|where|join|left|right|inner|outer|full|cross|on|using|" +
                "group|by|order|having|insert|into|values|update|set|delete|create|replace|" +
                "table|index|view|trigger|schema|database|drop|alter|add|column|primary|" +
                "foreign|references|unique|default|not|null|and|or|as|distinct|union|all|" +
                "limit|offset|case|when|then|else|end|begin|commit|rollback|with|desc|asc|" +
                "exists|between|like|in|is|if|return|declare|cast|count|sum|avg|min|max)\\b",
        ),
        TokenKind.Keyword,
    ),
    CodeRule(Regex("\\b\\d+(?:\\.\\d+)?\\b"), TokenKind.Number),
)

private val POWERSHELL_RULES = listOf(
    CodeRule(Regex("<#[\\s\\S]*?#>"), TokenKind.Comment),
    CodeRule(Regex("#[^\\n]*"), TokenKind.Comment),
    CodeRule(Regex("\"(?:`.|[^\"\\n])*\"|'(?:''|[^'\\n])*'"), TokenKind.String),
    CodeRule(Regex("\\$\\{[^}\\n]+\\}|\\$[\\w:]+"), TokenKind.Variable),
    CodeRule(Regex("(?m)^[ \\t]*[A-Z][a-zA-Z]+-[A-Z][a-zA-Z]+"), TokenKind.Keyword),
    CodeRule(
        Regex(
            "\\b(?i:if|else|elseif|foreach|for|while|do|switch|function|param|return|try|" +
                "catch|finally|throw|begin|process|end|filter|in|not|and|or|class|enum)\\b",
        ),
        TokenKind.Keyword,
    ),
    CodeRule(Regex("(?<=[ \\t])-[A-Za-z][\\w-]*"), TokenKind.Attr),
    CodeRule(Regex("\\b\\d+(?:\\.\\d+)?\\b"), TokenKind.Number),
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

private val DIFF_RULES = listOf(
    CodeRule(Regex("(?m)^@@[^\\n]*"), TokenKind.Comment),
    CodeRule(Regex("(?m)^\\+[^\\n]*"), TokenKind.String),
    CodeRule(Regex("(?m)^-[^\\n]*"), TokenKind.Key),
    CodeRule(Regex("(?m)^(?:diff|index|---|\\+\\+\\+)[^\\n]*"), TokenKind.Comment),
)

private val MARKDOWN_RULES = listOf(
    CodeRule(Regex("(?m)^#{1,6}[ \\t][^\\n]*"), TokenKind.Keyword),
    CodeRule(Regex("`[^`\\n]+`"), TokenKind.String),
    CodeRule(Regex("(?m)^[ \\t]*>[^\\n]*"), TokenKind.Comment),
    CodeRule(Regex("\\*\\*[^*\\n]+\\*\\*|__[^_\\n]+__"), TokenKind.Tag),
    CodeRule(Regex("\\[[^\\]\\n]*\\]\\([^)\\n]*\\)"), TokenKind.Variable),
)

/** 仓库里出现频率最高、而 Highlights 不覆盖的语言。 */
private val RULE_SETS: Map<String, List<CodeRule>> = mapOf(
    "json" to JSON_RULES,
    "jsonc" to JSON_RULES,
    "json5" to JSON_RULES,
    "yaml" to YAML_RULES,
    "yml" to YAML_RULES,
    "toml" to INI_RULES,
    "ini" to INI_RULES,
    "cfg" to INI_RULES,
    "conf" to INI_RULES,
    "properties" to INI_RULES,
    "env" to INI_RULES,
    "dotenv" to INI_RULES,
    "xml" to MARKUP_RULES,
    "html" to MARKUP_RULES,
    "htm" to MARKUP_RULES,
    "svg" to MARKUP_RULES,
    "vue" to MARKUP_RULES,
    "xaml" to MARKUP_RULES,
    "sql" to SQL_RULES,
    "powershell" to POWERSHELL_RULES,
    "pwsh" to POWERSHELL_RULES,
    "ps1" to POWERSHELL_RULES,
    "psm1" to POWERSHELL_RULES,
    "bat" to BATCH_RULES,
    "batch" to BATCH_RULES,
    "cmd" to BATCH_RULES,
    "diff" to DIFF_RULES,
    "patch" to DIFF_RULES,
    "markdown" to MARKDOWN_RULES,
    "md" to MARKDOWN_RULES,
)
