package io.github.shinglem.markdown.adapter

import io.github.shinglem.markdown.MarkdownConfig
import io.github.shinglem.markdown.builtin.DesktopMathAdapter

/**
 * desktop 专有适配器。
 *
 * - **公式**：`ratex` 原生排版（Rust 核心 + Compose 矢量绘制，不需要浏览器），始终注册。
 *
 * 在 desktopMain：其它 target 没有原生公式引擎，落到 `builtin.source` 显示源码。
 */
internal actual fun platformAdapters(config: MarkdownConfig): List<BlockAdapter> = listOf(
    DesktopMathAdapter(config),
)
