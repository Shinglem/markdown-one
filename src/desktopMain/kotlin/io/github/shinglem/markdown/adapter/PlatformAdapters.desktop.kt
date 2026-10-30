package io.github.shinglem.markdown.adapter

import io.github.shinglem.markdown.MarkdownConfig
import io.github.shinglem.markdown.WebFallback
import io.github.shinglem.markdown.builtin.DesktopMathAdapter
import io.github.shinglem.markdown.web.JcefAdapter

/**
 * desktop 专有适配器。
 *
 * - **公式**：JLaTeXMath 原生排版（不需要浏览器），始终注册；
 * - **JCEF**：只在开关打开时注册；即使注册了，运行环境没有 `org.cef` 也会因
 *   [BlockAdapter.isAvailable] 为 false 而被路由跳过。
 *
 * 两者都在 desktopMain：其它 target 没有 AWT/CEF，落到 `builtin.source` 显示源码。
 */
internal actual fun platformAdapters(config: MarkdownConfig): List<BlockAdapter> = buildList {
    add(DesktopMathAdapter(config))
    if (config.webFallback is WebFallback.Jcef) add(JcefAdapter(config))
}
