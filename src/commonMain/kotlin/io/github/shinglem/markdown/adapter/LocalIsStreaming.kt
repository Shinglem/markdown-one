package io.github.shinglem.markdown.adapter

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 当前是否处于流式接收（回答尚未结束）。
 *
 * 适配器据此降级：mermaid 只占位不布局、JCEF 整链跳过、代码高亮照常。
 */
public val LocalIsStreaming: androidx.compose.runtime.ProvidableCompositionLocal<Boolean> =
    staticCompositionLocalOf { false }
