package io.github.shinglem.markdown

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.shinglem.markdown.adapter.AdapterDiagnostics
import io.github.shinglem.markdown.adapter.BlockAdapter
import io.github.shinglem.markdown.model.MdBlock

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
    /** 原生适配器画不出时的回落策略。 */
    public val webFallback: WebFallback = WebFallback.Source,
    /** 回落渲染的高度（浏览器内容在内部滚动）。 */
    public val webFallbackHeight: Dp = 320.dp,
    /** 为某个块生成交给浏览器渲染的 HTML 文档；返回 null 表示这个块不走回落渲染。
     *
     * 默认只处理「原始 HTML 块」（且需 [allowRawHtml]）。数学公式与图表需要 KaTeX / mermaid.js
     * 之类的脚本资源，由调用方在这里注入（库不打包脚本，保持零体积）。
     */
    public val webDocument: ((MdBlock) -> String?)? = null,
    /** 整体外观；null 表示沿用 Material 3 默认（[outsidePaperTheme] 是内置的纸面风格）。 */
    public val theme: MarkdownTheme? = null,
    /** 大块的限高滚动与缩放。 */
    public val viewport: BlockViewportConfig = BlockViewportConfig(),
    /** 同时存活的浏览器实例上限（每实例是一个 Chromium 渲染进程）。 */
    public val jcefPoolSize: Int = 2,
    /** 图表画不出时的提示文案。 */
    public val mermaidFallbackText: String = "图表语法暂不支持，已显示源码",
    /** 公式排版失败时的提示文案（desktop 用 JLaTeXMath 原生排版，其它端或语法不支持时回落源码）。 */
    public val mathFallbackText: String = "公式排版不可用，已显示源码",
    /** 超过这个长度的代码块跳过同步着色（大块同步着色会卡 UI）。 */
    public val maxSyncHighlightLength: Int = 8_000,
    /** 链接点击回调。 */
    public val onLinkClick: (String) -> Unit = {},
    /** 路由与失败诊断。 */
    public val diagnostics: AdapterDiagnostics = AdapterDiagnostics.None,
)
