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
    /** 流式期间是否允许 JCEF 适配器参与。默认不允许（每帧重载 Chromium 会抖）。 */
    public val jcefDuringStreaming: Boolean = false,
    /** 是否允许渲染原始 HTML 块（默认关，只显示源码）。 */
    public val allowRawHtml: Boolean = false,
    /** 图表画不出时的提示文案。 */
    public val mermaidFallbackText: String = "图表语法暂不支持，已显示源码",
    /** 超过这个长度的代码块跳过同步着色（大块同步着色会卡 UI）。 */
    public val maxSyncHighlightLength: Int = 8_000,
    /** 链接点击回调。 */
    public val onLinkClick: (String) -> Unit = {},
    /** 路由与失败诊断。 */
    public val diagnostics: AdapterDiagnostics = AdapterDiagnostics.None,
)
