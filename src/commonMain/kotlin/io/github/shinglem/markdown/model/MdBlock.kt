package io.github.shinglem.markdown.model

import io.github.shinglem.markdown.adapter.FormatId
import io.github.shinglem.markdown.adapter.Formats
import org.intellij.markdown.ast.ASTNode

/**
 * 解析后的块。与渲染完全解耦：任何适配器只依赖这个模型。
 *
 * [raw] 是块的原始 Markdown 源码，**必须保留** —— 任何适配器渲染失败都能就地回落成源码。
 * [source] 是整篇文档的源码：GFM AST 节点的 offset 是**文档级**的，默认块渲染器
 * （`text/MdInline.kt` 与 `text/MdBlocks.kt`）会用 `source.substring(node.startOffset, node.endOffset)`，
 * 因此必须传整篇而非块片段，否则会越界。
 */
public sealed interface MdBlock {
    /** 文档内的稳定标识，用于缓存与失效判定。 */
    public val key: String

    /** 整篇文档源码（AST offset 的坐标系）。 */
    public val source: String

    /** 该块的原始 Markdown 源码。 */
    public val raw: String

    /** 该块可接受的格式集合；适配器声明与之有交集即可参与竞争。 */
    public val formats: Set<FormatId>

    /** GFM AST 节点。 */
    public val node: ASTNode

    public data class Heading(
        override val key: String,
        override val source: String,
        override val raw: String,
        override val node: ASTNode,
        public val level: Int,
    ) : MdBlock {
        override val formats: Set<FormatId> get() = setOf(Formats.Markdown)
    }

    public data class Paragraph(
        override val key: String,
        override val source: String,
        override val raw: String,
        override val node: ASTNode,
    ) : MdBlock {
        override val formats: Set<FormatId> get() = setOf(Formats.Markdown)
    }

    public data class CodeBlock(
        override val key: String,
        override val source: String,
        override val raw: String,
        override val node: ASTNode,
        public val code: String,
        public val language: String?,
        public val fenced: Boolean,
        /** ```` ```kotlin {1,3-5} ```` 里要高亮的行号（1 基）。 */
        public val highlightLines: Set<Int> = emptySet(),
    ) : MdBlock {
        override val formats: Set<FormatId>
            get() = buildSet {
                add(Formats.code(language))
                val name = language?.trim()?.lowercase()
                if (name == "mermaid" || name == "mmd") add(Formats.Mermaid)
            }
    }

    public data class Quote(
        override val key: String,
        override val source: String,
        override val raw: String,
        override val node: ASTNode,
    ) : MdBlock {
        override val formats: Set<FormatId> get() = setOf(Formats.Markdown)
    }

    /**
     * GitHub Alerts（`> [!NOTE]` / `[!TIP]` / `[!IMPORTANT]` / `[!WARNING]` / `[!CAUTION]`）。
     *
     * GFM 解析器原生产出 `ALERT` 节点（标题是 `ALERT_TITLE` token，正文是其后的一串块），
     * 所以不需要预处理。不认识的标记（`[!FOO]`）按普通引用解析，见 `MdBlockFactory`。
     */
    public data class Alert(
        override val key: String,
        override val source: String,
        override val raw: String,
        override val node: ASTNode,
        public val kind: AlertKind,
    ) : MdBlock {
        override val formats: Set<FormatId> get() = setOf(Formats.Markdown, Formats.Alert)
    }

    public data class ListBlock(
        override val key: String,
        override val source: String,
        override val raw: String,
        override val node: ASTNode,
        public val ordered: Boolean,
    ) : MdBlock {
        override val formats: Set<FormatId> get() = setOf(Formats.Markdown)
    }

    public data class Table(
        override val key: String,
        override val source: String,
        override val raw: String,
        override val node: ASTNode,
    ) : MdBlock {
        // 同时带 `markdown`：表格仍然由 markdown 适配器渲染，
        // 额外带上 `table` 只是为了让 BlockViewportConfig 能单独给表格限高加滚动条。
        override val formats: Set<FormatId> get() = setOf(Formats.Markdown, Formats.Table)
    }

    public data class Rule(
        override val key: String,
        override val source: String,
        override val raw: String,
        override val node: ASTNode,
    ) : MdBlock {
        override val formats: Set<FormatId> get() = setOf(Formats.Markdown)
    }

    public data class Image(
        override val key: String,
        override val source: String,
        override val raw: String,
        override val node: ASTNode,
        public val url: String,
        public val alt: String,
    ) : MdBlock {
        override val formats: Set<FormatId> get() = setOf(Formats.Image)
    }

    public data class Html(
        override val key: String,
        override val source: String,
        override val raw: String,
        override val node: ASTNode,
    ) : MdBlock {
        override val formats: Set<FormatId> get() = setOf(Formats.Html)
    }

    public data class Math(
        override val key: String,
        override val source: String,
        override val raw: String,
        override val node: ASTNode,
        public val tex: String,
        public val display: Boolean,
    ) : MdBlock {
        override val formats: Set<FormatId> get() = setOf(Formats.Math)
    }

    /**
     * 无法归类的块（含流式中途未完成的块）。
     * [type] 给适配器/诊断用，例如 `incomplete`。
     */
    public data class Custom(
        override val key: String,
        override val source: String,
        override val raw: String,
        override val node: ASTNode,
        public val type: String,
    ) : MdBlock {
        override val formats: Set<FormatId>
            get() = if (type.isBlank()) emptySet() else setOf(FormatId(type))
    }

    /**
     * 脚注定义区（文档末尾，由 `parse/FootnoteNormalizer.kt` 抽出来的定义）。
     *
     * 没有对应的 AST 节点（脚注不是 CommonMark/GFM 规范能力），[node] 用文档根节点占位；
     * 渲染只依赖 [items]，不读节点。没有定义时解析器不会产出这个块。
     */
    public data class Footnotes(
        override val key: String,
        override val source: String,
        override val raw: String,
        override val node: ASTNode,
        public val items: List<FootnoteItem>,
    ) : MdBlock {
        override val formats: Set<FormatId> get() = setOf(Formats.Markdown, Formats.Footnotes)
    }
}

/** 一条脚注：序号（与正文上标一致）、id 与定义正文（Markdown）。 */
public data class FootnoteItem(
    public val id: String,
    public val number: Int,
    public val markdown: String,
)

/** 解析结果。 */
public data class MdDocument(
    public val blocks: List<MdBlock>,
    public val streaming: Boolean,
)

/** GitHub Alerts 的 5 种类型。 */
public enum class AlertKind {
    Note,
    Tip,
    Important,
    Warning,
    Caution,
    ;

    public companion object {
        /** `[!NOTE]` 里取出 `NOTE`（大小写不敏感）；不认识的标记返回 null。 */
        public fun fromMarker(marker: String): AlertKind? =
            when (marker.trim().lowercase()) {
                "note" -> Note
                "tip" -> Tip
                "important" -> Important
                "warning" -> Warning
                "caution" -> Caution
                else -> null
            }
    }
}
