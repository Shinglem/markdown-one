package io.github.shinglem.markdown

import io.github.shinglem.markdown.adapter.AdapterDiagnostics
import io.github.shinglem.markdown.adapter.BlockAdapter

/**
 * 渲染配置。
 *
 * [adapters] 里的条目优先于自动发现的内置适配器（同 [BlockAdapter.id] 时覆盖后者）。
 */
public data class MarkdownConfig(
    /** 额外或覆盖用的适配器。 */
    public val adapters: List<BlockAdapter> = emptyList(),
    /** 按 id 禁用内置适配器（自动发现的）。 */
    public val disabledAdapterIds: Set<String> = emptySet(),
    /** 是否允许渲染原始 HTML 块（默认关，只显示源码）。 */
    public val allowRawHtml: Boolean = false,
    /** 整体外观；null 表示沿用 Material 3 默认（[outsidePaperTheme] 是内置的纸面风格）。 */
    public val theme: MarkdownTheme? = null,
    /** 大块的限高滚动与缩放。 */
    public val viewport: BlockViewportConfig = BlockViewportConfig(),
    /** 图表画不出时的提示文案。 */
    public val mermaidFallbackText: String = "图表语法暂不支持，已显示源码",
    /** 公式排版失败时的提示文案（desktop 用 ratex 原生排版，其它端或测量失败时回落源码）。 */
    public val mathFallbackText: String = "公式排版不可用，已显示源码",
    /** 超过这个长度的代码块跳过同步着色（大块同步着色会卡 UI）。 */
    public val maxSyncHighlightLength: Int = 8_000,
    /** 链接点击回调。 */
    public val onLinkClick: (String) -> Unit = {},
    /** 路由与失败诊断。 */
    public val diagnostics: AdapterDiagnostics = AdapterDiagnostics.None,
)
