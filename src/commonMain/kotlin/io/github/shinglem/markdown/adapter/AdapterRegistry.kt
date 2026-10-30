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
    public fun select(block: MdBlock, streaming: Boolean, allowJcef: Boolean = !streaming): BlockAdapter? =
        all.asSequence()
            .filter { adapter -> adapter.kind != AdapterKind.Jcef || allowJcef }
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

/** 平台专有适配器（例如 desktop 的 JCEF 回落）。 */
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
    io.github.shinglem.markdown.builtin.MarkdownBlockAdapter(config),
    io.github.shinglem.markdown.builtin.CodeHighlightAdapter(config),
    io.github.shinglem.markdown.builtin.MermaidAdapter(config),
    io.github.shinglem.markdown.builtin.SourceFallbackAdapter,
) + platformAdapters(config)
