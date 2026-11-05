package io.github.shinglem.markdown.adapter

import io.github.shinglem.markdown.MarkdownConfig
import io.github.shinglem.markdown.model.MdBlock

/**
 * 适配器集合与路由。
 *
 * 顺序即优先级：同 [BlockAdapter.priority] 时**先注册者胜**。显式注册
 * （[MarkdownConfig.adapters]）排在内置与自动发现之前。
 */
public class AdapterRegistry(adapters: List<BlockAdapter>) {

    public val all: List<BlockAdapter> = adapters

    /** 为一个块选适配器；返回 null 表示需要由调用方走兜底。 */
    public fun select(block: MdBlock): BlockAdapter? =
        all.asSequence()
            .filter { adapter -> adapter.matches(block) }
            .filter { adapter -> adapter.isAvailable() }
            .filter { adapter -> adapter.canRender(block) }
            .maxByOrNull { it.priority }

    private fun BlockAdapter.matches(block: MdBlock): Boolean {
        val declared = formats()
        return declared.any { format ->
            when {
                format == Formats.Any -> true
                format in block.formats -> true
                // 前缀通配：`code:*` 命中任意 `code:<lang>`
                format.value.endsWith("*") ->
                    block.formats.any { it.value.startsWith(format.value.dropLast(1)) }

                else -> false
            }
        }
    }
}

/** 自动发现：JVM/desktop 用 ServiceLoader，其它端返回空（靠显式注册）。 */
internal expect fun discoverAdapterFactories(): List<BlockAdapterFactory>

/** 平台专有适配器（例如 desktop 的公式引擎）。 */
internal expect fun platformAdapters(config: MarkdownConfig): List<BlockAdapter>

/** 组装最终 registry：显式 → 自动发现 → 内置，按 id 去重（先到者胜）。 */
public fun buildAdapterRegistry(config: MarkdownConfig): AdapterRegistry {
    val seen = LinkedHashMap<String, BlockAdapter>()

    fun add(adapter: BlockAdapter) {
        if (adapter.id in config.disabledAdapterIds) return
        seen.putIfAbsent(adapter.id, adapter)
    }

    config.adapters.forEach(::add)
    discoverAdapterFactories().forEach { factory -> factory.create().forEach(::add) }
    builtinAdapters(config).forEach(::add)
    return AdapterRegistry(seen.values.toList())
}

/**
 * 内置适配器。集中在一处，便于测试与 demo 面板枚举能力。
 */
public fun builtinAdapters(config: MarkdownConfig): List<BlockAdapter> = listOf(
    io.github.shinglem.markdown.builtin.TextBlockAdapter(config),
    // 图表排在代码高亮之前：同优先级时先注册者胜，两者都声明 `code:*`，
    // 由 DiagramAdapter.canRender 里的语言判定决定谁真正接手。
    io.github.shinglem.markdown.builtin.DiagramAdapter(config),
    io.github.shinglem.markdown.builtin.CodeHighlightAdapter(config),
    io.github.shinglem.markdown.builtin.FootnotesAdapter(config),
    io.github.shinglem.markdown.builtin.DetailsAdapter(config),
    // 原生 HTML 渲染优先于「剥壳递归」兜底（priority 115 > 105）
    io.github.shinglem.markdown.builtin.HtmlNativeAdapter(config),
    io.github.shinglem.markdown.builtin.HtmlContainerAdapter(config),
    io.github.shinglem.markdown.builtin.SourceFallbackAdapter,
) + platformAdapters(config)
