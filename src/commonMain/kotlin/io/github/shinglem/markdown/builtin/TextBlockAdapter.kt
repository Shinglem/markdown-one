package io.github.shinglem.markdown.builtin

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.shinglem.markdown.MarkdownConfig
import io.github.shinglem.markdown.adapter.AdapterKind
import io.github.shinglem.markdown.adapter.BlockAdapter
import io.github.shinglem.markdown.adapter.FormatId
import io.github.shinglem.markdown.adapter.Formats
import io.github.shinglem.markdown.model.MdBlock
import io.github.shinglem.markdown.text.MdHeading
import io.github.shinglem.markdown.text.MdParagraph
import io.github.shinglem.markdown.text.MdRule

/**
 * 默认的 Markdown 块渲染器（id 仍是 `builtin.markdown`，诊断面板与宿主配置不用改）。
 *
 * 这是「自建遍历层」的兜底实现 —— 有专门适配器的块（代码/mermaid/数学/HTML）
 * 会先被更高优先级的适配器接管，这里只处理标题/段落/列表/表格/引用/分割线。
 * 文本与块组件都是本库自己的实现（`io.github.shinglem.markdown.text`）。
 */
internal class TextBlockAdapter(@Suppress("unused") private val config: MarkdownConfig) : BlockAdapter {
    override val id: String = "builtin.markdown"
    override val kind: AdapterKind = AdapterKind.Native
    override val priority: Int = 100

    override fun formats(): Set<FormatId> = setOf(Formats.Markdown)

    override fun isAvailable(): Boolean = true

    override fun canRender(block: MdBlock): Boolean = when (block) {
        is MdBlock.Heading,
        is MdBlock.Paragraph,
        is MdBlock.Quote,
        is MdBlock.ListBlock,
        is MdBlock.Table,
        is MdBlock.Rule,
        -> true

        else -> false
    }

    @Composable
    override fun Render(block: MdBlock, modifier: Modifier) {
        when (block) {
            is MdBlock.Heading -> MdHeading(block.source, block.node, block.level, modifier)
            is MdBlock.Paragraph -> MdParagraph(block.source, block.node, modifier)
            is MdBlock.Rule -> MdRule(modifier)
            else -> SourceFallbackAdapter.Render(block, modifier)
        }
    }
}
