package io.github.shinglem.markdown.adapter

/**
 * 一个块/内容类型的标识。
 *
 * 约定（不是枚举，方便扩展）：
 * - `markdown`      普通 Markdown 块（标题/段落/列表/表格/引用/分割线）
 * - `code:<lang>`   代码块，语言名小写；无语言时为 `code:`
 * - `mermaid`       mermaid 图表（`code:mermaid` 的块同时声明它）
 * - `math`          数学公式
 * - `html`          原始 HTML 块
 * - `image.generic` 图片
 * - `*`             通配（仅兜底适配器使用）
 */
@JvmInline
public value class FormatId(public val value: String) {
    init {
        require(value.isNotBlank()) { "FormatId 不能为空" }
    }

    override fun toString(): String = value
}

public object Formats {
    public val Markdown: FormatId = FormatId("markdown")
    public val Mermaid: FormatId = FormatId("mermaid")
    public val Math: FormatId = FormatId("math")
    public val Html: FormatId = FormatId("html")
    public val Table: FormatId = FormatId("table")
    public val Image: FormatId = FormatId("image.generic")
    public val Any: FormatId = FormatId("*")

    /** 前缀通配：匹配所有 `code:<lang>`。 */
    public val Code: FormatId = FormatId("code:*")

    public fun code(language: String?): FormatId =
        FormatId("code:" + language?.trim()?.lowercase().orEmpty())
}
