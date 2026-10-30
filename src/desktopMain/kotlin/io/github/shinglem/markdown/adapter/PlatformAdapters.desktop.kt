package io.github.shinglem.markdown.adapter

import io.github.shinglem.markdown.MarkdownConfig
import io.github.shinglem.markdown.WebFallback
import io.github.shinglem.markdown.web.JcefAdapter

/**
 * desktop 专有适配器。
 *
 * JCEF 适配器只在开关打开时注册；即使注册了，运行环境没有 `org.cef` 也会因
 * [BlockAdapter.isAvailable] 为 false 而被路由跳过。
 */
internal actual fun platformAdapters(config: MarkdownConfig): List<BlockAdapter> =
    if (config.webFallback is WebFallback.Jcef) listOf(JcefAdapter(config)) else emptyList()
