package io.github.shinglem.markdown.builtin

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.markdownTypography
import io.github.shinglem.markdown.MarkdownConfig
import io.github.shinglem.markdown.adapter.AdapterKind
import io.github.shinglem.markdown.adapter.BlockAdapter
import io.github.shinglem.markdown.adapter.FormatId
import io.github.shinglem.markdown.adapter.Formats
import io.github.shinglem.markdown.model.MdBlock

/**
 * 默认的 Markdown 块渲染器：把块交回 mikepenz 的 element 实现。
 *
 * 这是「自建遍历层」的兜底实现 —— 有专门适配器的块（代码/mermaid/数学/HTML）
 * 会先被更高优先级的适配器接管，这里只处理标题/段落/列表/表格/引用/分割线。
 */
internal class MarkdownBlockAdapter(@Suppress("unused") private val config: MarkdownConfig) : BlockAdapter {
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
        val components = markdownComponents()
        val model = MarkdownComponentModel(
            content = block.source,
            node = block.node,
            typography = markdownTypography(),
        )
        val container = modifier.fillMaxWidth()
        when (block) {
            is MdBlock.Heading -> when (block.level) {
                1 -> components.heading1(model)
                2 -> components.heading2(model)
                3 -> components.heading3(model)
                4 -> components.heading4(model)
                5 -> components.heading5(model)
                else -> components.heading6(model)
            }

            is MdBlock.Quote -> components.blockQuote(model)
            is MdBlock.ListBlock -> if (block.ordered) components.orderedList(model) else components.unorderedList(model)
            is MdBlock.Table -> components.table(model)
            is MdBlock.Rule -> components.horizontalRule(model)
            else -> components.paragraph(model)
        }
        // container 仅用于保持 fillMaxWidth 语义；element 自身决定实际尺寸
        @Suppress("UNUSED_EXPRESSION")
        container
    }
}
