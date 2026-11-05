package io.github.shinglem.markdown.builtin

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import io.github.shinglem.markdown.MarkdownConfig
import io.github.shinglem.markdown.adapter.AdapterKind
import io.github.shinglem.markdown.adapter.BlockAdapter
import io.github.shinglem.markdown.adapter.FormatId
import io.github.shinglem.markdown.adapter.Formats
import io.github.shinglem.markdown.model.MdBlock
import io.github.shinglem.markdown.text.LocalMdOnImageClick

/**
 * 独占一行的图片：块级渲染，**限高 + 点击放大**。
 *
 * 以前这类图片落在 `Paragraph` 里走行内渲染，结果是「压到可用宽度就完事」：
 * 大截图（2000×3000 的报错图）压到 600dp 后完全看不清，也没有任何交互。
 *
 * 限高用 420dp，与 [io.github.shinglem.markdown.BlockViewportConfig] 的默认值一致，
 * 让"大图不会把一条消息撑到几屏"这件事与图表/表格保持一致。
 */
internal class ImageBlockAdapter(private val config: MarkdownConfig) : BlockAdapter {

    override val id: String = "builtin.image"
    override val kind: AdapterKind = AdapterKind.Native
    override val priority: Int = 100

    override fun formats(): Set<FormatId> = setOf(Formats.Image)

    override fun isAvailable(): Boolean = true

    override fun canRender(block: MdBlock): Boolean = block is MdBlock.Image

    @Composable
    override fun Render(block: MdBlock, modifier: Modifier) {
        val image = block as MdBlock.Image
        val painter = rememberAsyncImagePainter(model = image.url)
        val state by painter.state.collectAsState()
        // 加载完才拿得到真实宽高比：先按默认比例占位，成功后收敛。
        var ratio by remember(image.url) { mutableStateOf(0f) }
        (state as? AsyncImagePainter.State.Success)?.let { success ->
            val result = success.result.image
            if (result.width > 0 && result.height > 0) {
                val detected = result.height.toFloat() / result.width.toFloat()
                if (detected != ratio) ratio = detected
            }
        }
        val onImageClick = LocalMdOnImageClick.current

        Column(
            modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        ) {
            when (state) {
                is AsyncImagePainter.State.Error -> ImageFallback(image.url, image.alt, config.imageFallbackText)
                is AsyncImagePainter.State.Loading, AsyncImagePainter.State.Empty ->
                    // 占位高度按 3:2 估，避免加载完再跳一次布局
                    Box(Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 420.dp))

                else -> Image(
                    painter = painter,
                    contentDescription = image.alt.ifBlank { null },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .clickable { onImageClick(image.url, image.alt) },
                )
            }
            image.alt.takeIf { it.isNotBlank() }?.let { alt ->
                Text(
                    text = alt,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** 图片加载失败不留空白：给地址与提示，用户能判断是网络问题还是链接本身错了。 */
@Composable
private fun ImageFallback(url: String, alt: String, fallbackText: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = fallbackText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (alt.isBlank()) url else "$alt — $url",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
