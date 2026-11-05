package io.github.shinglem.markdown.text

/**
 * 标题 → 锚点 id（GitHub 的 slug 规则，简化版）。
 *
 * AI 生成的目录写成 `- [快速开始](#快速开始)`，要与标题的锚点对上才能跳转。
 * 规则：小写 → 去掉行内标记残留 → 空白转 `-` → 删掉除字母/数字/中日韩/`-`/`_` 之外的字符。
 *
 * 与 GitHub 的差异：不处理重复标题的 `-1` 编号（重复标题时都跳到第一个同名的）。
 * 那需要整篇统计，收益很小；真正的重复标题在 AI 回答里几乎不出现。
 */
internal fun headingAnchor(text: String): String = buildString {
    var pendingDash = false
    text.trim().lowercase().forEach { char ->
        when {
            char.isWhitespace() -> pendingDash = isNotEmpty()
            char.isLetterOrDigit() || char == '_' || char == '-' -> {
                if (pendingDash && isNotEmpty() && last() != '-') append('-')
                pendingDash = false
                append(char)
            }
            // 标点、emoji、`*` `_` 之类一律丢掉
            else -> Unit
        }
    }
}.trim('-')

/**
 * 脚注锚点前缀（`FootnoteNormalizer` 生成的 `#fn-<id>`）。
 *
 * 与 [FOOTNOTE_LINK_PREFIX] 同源：链接里是带 `#` 的，这里存的是去掉 `#` 之后的形式。
 */
internal const val FOOTNOTE_ANCHOR_PREFIX = "fn-"
