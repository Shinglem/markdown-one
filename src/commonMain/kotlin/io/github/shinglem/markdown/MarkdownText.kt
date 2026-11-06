package io.github.shinglem.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import io.github.shinglem.markdown.adapter.LocalIsStreaming
import io.github.shinglem.markdown.adapter.buildAdapterRegistry
import io.github.shinglem.markdown.model.MdBlock
import io.github.shinglem.markdown.parse.GfmMarkdownParser
import io.github.shinglem.markdown.text.LocalMdAdapterRegistry
import io.github.shinglem.markdown.text.LocalMdAnchorScroll
import io.github.shinglem.markdown.text.headingAnchor
import io.github.shinglem.markdown.text.FOOTNOTE_ANCHOR_PREFIX
import kotlinx.coroutines.launch
import io.github.shinglem.markdown.text.LocalMdColors
import io.github.shinglem.markdown.text.LocalMdDimens
import io.github.shinglem.markdown.text.LocalMdMathContent
import io.github.shinglem.markdown.text.LocalMdMaxSyncHighlightLength
import io.github.shinglem.markdown.text.LocalMdOnImageClick
import io.github.shinglem.markdown.text.LocalMdOnLinkClick
import io.github.shinglem.markdown.text.LocalMdPadding
import io.github.shinglem.markdown.text.LocalMdReferences
import io.github.shinglem.markdown.text.LocalMdTypography
import io.github.shinglem.markdown.text.collectLinkDefinitions
import io.github.shinglem.markdown.text.mdColors
import io.github.shinglem.markdown.text.mdDimens
import io.github.shinglem.markdown.text.mdPadding
import io.github.shinglem.markdown.text.mdTypography
import io.github.shinglem.markdown.text.MdImagePreview
import io.github.shinglem.markdown.text.withTextAlign

/**
 * 渲染入口。
 *
 * 流程：解析成块 → 逐块向适配器链要渲染器 → 全部处理不了时由源码兜底适配器接管。
 * 块之间用 [Column] 排列（不嵌套 LazyColumn，避免无界高度约束导致崩溃）。
 *
 * 这里铺的是本库自己的一套 CompositionLocal（`io.github.shinglem.markdown.text`），
 * 文本与块组件都从这里取主题，不再依赖第三方 renderer 的模型。
 */
@Composable
public fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
    config: MarkdownConfig = MarkdownConfig(),
    textAlign: TextAlign? = null,
    fillWidth: Boolean = true,
    /**
     * 是否包一层 [SelectionContainer]。
     *
     * 关掉有实际用途：流式输出时内容每几十毫秒变一次，选区与拖动手柄反复重建会闪烁；
     * 宿主可以在「正在接收」的那条消息上关掉，稳定后再打开。
     */
    selectable: Boolean = true,
) {
    val registry = remember(config) { buildAdapterRegistry(config) }
    val document = remember(markdown, streaming) { GfmMarkdownParser().parse(markdown, streaming) }

    // 主题可在运行时切换（明暗），所以逐个取当前值，而不是写死默认。
    val theme = config.theme
    val colors = theme?.colors ?: mdColors()
    val baseTypography = theme?.typography ?: mdTypography()
    val typography = remember(baseTypography, textAlign) {
        if (textAlign == null) baseTypography else baseTypography.withTextAlign(textAlign)
    }
    val dimens = theme?.dimens ?: mdDimens()
    val padding = theme?.padding ?: mdPadding()

    // 行内公式：先按整篇收集 tex 建表，构建器里按 key 命中同一条。
    // 颜色与字号显式传下去 —— 这时还不在主题的 CompositionLocal 作用域里。
    val source = document.blocks.firstOrNull()?.source ?: markdown
    val inlineTexes = remember(document.blocks) { collectInlineTexes(document.blocks, source) }
    val mathContents = mathInlineContents(inlineTexes, colors.text, typography.text.fontSize)
    val references = remember(document.blocks) {
        collectLinkDefinitions(source, document.blocks.map { it.node })
    }

    // 图片全屏预览：库自己接管，宿主不必实现（行内与块级图片共用一个入口）。
    var preview by remember(markdown) { mutableStateOf<Pair<String, String>?>(null) }
    val onImageClick: (String, String) -> Unit = { url, alt -> preview = url to alt }

    // ---- 文内跳转（锚点）----
    // 锚点 id 由块本身决定：标题用 slug，脚注区用固定 id。跳转时按 id 找块，再请求把它滚进视野。
    val anchorOf: Map<String, String> = remember(document.blocks) {
        document.blocks.mapNotNull { block ->
            anchorIdOf(block)?.let { anchor -> block.key to anchor }
        }.toMap()
    }
    // 脚注**回跳**锚点：`#fnref-<id>` → 引用所在块。一个块可能有多处引用，所以是「多对一」，
    // 塞不进 anchorOf（那个是 blockKey → 单个 anchorId）。
    val footnoteRefAnchors: Map<String, String> = remember(document.blocks) {
        document.blocks.flatMap { block ->
            FNREF_IN_RAW.findAll(block.raw).map { "fnref-${it.groupValues[1]}" to block.key }
        }.toMap()
    }
    val anchorKeys: Map<String, String> = remember(anchorOf, footnoteRefAnchors) {
        anchorOf.entries.associate { (key, anchor) -> anchor to key } + footnoteRefAnchors
    }
    // 需要注册 BringIntoViewRequester 的块：自己就是锚点的，以及被脚注回跳指向的。
    val anchoredBlocks: Set<String> = remember(anchorOf, footnoteRefAnchors) {
        anchorOf.keys + footnoteRefAnchors.values
    }
    val requesters = remember(markdown) { mutableMapOf<String, BringIntoViewRequester>() }
    val anchorScope = rememberCoroutineScope()
    // 外层文档的跳转入口：嵌套的 `MarkdownText`（列表项、引用、脚注定义里各有一份）
    // 自己解析不到的片段要冒泡出去，否则脚注区的「↩」指向的锚点只存在于外层，点了没反应。
    // 必须在下面的 CompositionLocalProvider 之前读，否则读到的是自己。
    val outerAnchorScroll = LocalMdAnchorScroll.current
    val onAnchorScroll: (String) -> Unit = { fragment ->
        val target = resolveAnchorKey(fragment, anchorKeys)
        val requester = target?.let { requesters[it] }
        if (requester != null) {
            anchorScope.launch { runCatching { requester.bringIntoView() } }
        } else if (target == null) {
            outerAnchorScroll(fragment)
        }
    }

    Box(
        modifier = if (theme != null && theme.background != Color.Unspecified) {
            modifier.background(theme.background)
        } else {
            modifier
        },
    ) {
        CompositionLocalProvider(
            LocalIsStreaming provides streaming,
            LocalMdColors provides colors,
            LocalMdTypography provides typography,
            LocalMdDimens provides dimens,
            LocalMdPadding provides padding,
            LocalMdMathContent provides mathContents,
            LocalMdReferences provides references,
            LocalMdAdapterRegistry provides registry,
            LocalMdOnLinkClick provides config.onLinkClick,
            LocalMdOnImageClick provides onImageClick,
            LocalMdAnchorScroll provides onAnchorScroll,
            LocalMdMaxSyncHighlightLength provides config.maxSyncHighlightLength,
        ) {
            // 选择框只包一次：每块一个 SelectionContainer 会让跨块选区断成若干段。
            val content: @Composable () -> Unit = {
                Column(if (fillWidth) Modifier.fillMaxWidth() else Modifier) {
                    document.blocks.forEach { block ->
                        val adapter = registry.select(block)
                        if (adapter != null) {
                            // 用块内容参与 key：切到另一个文档时块 key 仍是 b0/b1，只按 key 去重会漏报。
                            LaunchedEffect(block.key, block.raw, adapter.id) {
                                config.diagnostics.onRoute(block.key, block.formats, adapter.id, adapter.kind)
                            }
                            key(block.key) {
                                val blockContent: @Composable () -> Unit = {
                                    adapter.Render(
                                        block,
                                        if (fillWidth) Modifier.fillMaxWidth() else Modifier,
                                    )
                                }
                                val needsViewport = config.viewport.enabled &&
                                    config.viewport.matches(block.formats.map { it.value }.toSet())
                                val inner = @Composable {
                                    if (needsViewport) {
                                        BlockViewport(config.viewport, blockContent)
                                    } else {
                                        blockContent()
                                    }
                                }
                                if (block.key in anchoredBlocks) {
                                    // 锚点挂在块的最外层：跳转要把整块（含视口）滚进视野
                                    val requester = remember(block.key) { BringIntoViewRequester() }
                                    SideEffect { requesters[block.key] = requester }
                                    Box(Modifier.bringIntoViewRequester(requester)) { inner() }
                                } else {
                                    inner()
                                }
                            }
                        }
                    }
                }
            }
            if (selectable) SelectionContainer(content = content) else content()
        }
    }

    preview?.let { (url, alt) ->
        MdImagePreview(url = url, alt = alt, onDismiss = { preview = null })
    }
}

/** 脚注区的锚点 id（`#fn-xxx` 找不到精确目标时回退到它）。 */
private const val ANCHOR_FOOTNOTES = "footnotes"

/**
 * 块原文里的脚注引用（`FootnoteNormalizer` 已经把 `[^id]` 改写成 `[n](#fn-id)`）。
 * 用来给**回跳**（脚注定义区的「↩」）建锚点。
 */
private val FNREF_IN_RAW = Regex("""\(#fn-([^)\s]+)\)""")

/**
 * 块 → 锚点 id。
 *
 * - 标题：GitHub 风格的 slug（`## 快速开始` → `快速开始`）；
 * - 脚注区：固定 id，供 `#fn-<id>` 回退用（逐条定位要拆脚注渲染，收益不抵复杂度）。
 */
internal fun anchorIdOf(block: MdBlock): String? = when (block) {
    is MdBlock.Heading -> headingAnchor(block.raw.trimStart('#').trim()).takeIf { it.isNotEmpty() }
    is MdBlock.Footnotes -> ANCHOR_FOOTNOTES
    else -> null
}

/**
 * 把链接里的片段解析成块的 key。
 *
 * 顺序：精确匹配 → 对片段再做一次 slug 归一化（作者写的锚点可能带空格/大写）→
 * `fn-` 前缀回退到脚注区。都找不到返回 null（静默，不报错也不打开浏览器）。
 */
internal fun resolveAnchorKey(fragment: String, anchorKeys: Map<String, String>): String? {
    val raw = fragment.removePrefix("#").trim()
    if (raw.isEmpty()) return null
    anchorKeys[raw]?.let { return it }
    anchorKeys[headingAnchor(raw)]?.let { return it }
    if (raw.startsWith(FOOTNOTE_ANCHOR_PREFIX)) return anchorKeys[ANCHOR_FOOTNOTES]
    return null
}
