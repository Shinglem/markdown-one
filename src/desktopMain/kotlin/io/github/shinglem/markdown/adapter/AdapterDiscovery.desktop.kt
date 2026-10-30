package io.github.shinglem.markdown.adapter

import java.util.ServiceLoader

/**
 * JVM / desktop 的自动发现：读 `META-INF/services` 里登记的 [BlockAdapterFactory]。
 *
 * 其它 target 没有 ServiceLoader，由各自的 actual 返回空列表（靠显式注册）。
 */
internal actual fun discoverAdapterFactories(): List<BlockAdapterFactory> = runCatching {
    ServiceLoader.load(BlockAdapterFactory::class.java, BlockAdapterFactory::class.java.classLoader).toList()
}.getOrElse { emptyList() }
