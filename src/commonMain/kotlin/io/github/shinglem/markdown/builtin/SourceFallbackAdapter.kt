package io.github.shinglem.markdown.builtin

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import io.github.shinglem.markdown.adapter.AdapterKind
import io.github.shinglem.markdown.adapter.BlockAdapter
import io.github.shinglem.markdown.adapter.FormatId
import io.github.shinglem.markdown.adapter.Formats
import io.github.shinglem.markdown.model.MdBlock

/**
 * 最后一级：显示源码。
 *
 * 任何块在没有任何适配器能处理时都落到这里 —— **不允许留空白**。
 */
internal object SourceFallbackAdapter : BlockAdapter {
    override val id: String = "builtin.source"
    override val kind: AdapterKind = AdapterKind.Fallback
    override val priority: Int = 0

    override fun formats(): Set<FormatId> = setOf(Formats.Any)

    override fun isAvailable(): Boolean = true

    override fun canRender(block: MdBlock): Boolean = true

    @Composable
    override fun Render(block: MdBlock, modifier: Modifier) {
        Text(
            text = block.raw,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            modifier = modifier.fillMaxWidth(),
        )
    }
}
