package io.github.shinglem.markdown.builtin

import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.elements.MarkdownCheckBox as M3MarkdownCheckBox

/**
 * 本库的默认组件表。
 *
 * 只覆盖一处：`checkbox`。mikepenz 的 core 模块默认把 `- [x]` 画成 **`[x] ` 文本**，
 * 真正的 Material Checkbox 在 `-m3` 模块里；不覆盖的话任务列表看起来像没渲染。
 */
internal fun projectMarkdownComponents(): MarkdownComponents = markdownComponents(
    checkbox = { model ->
        M3MarkdownCheckBox(
            content = model.content,
            node = model.node,
            style = model.typography.text,
        )
    },
)
