package io.github.shinglem.markdown.web

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.shinglem.markdown.MarkdownConfig
import io.github.shinglem.markdown.adapter.AdapterKind
import io.github.shinglem.markdown.adapter.BlockAdapter
import io.github.shinglem.markdown.adapter.FormatId
import io.github.shinglem.markdown.adapter.Formats
import io.github.shinglem.markdown.adapter.LocalIsStreaming
import io.github.shinglem.markdown.model.MdBlock
import io.github.shinglem.markdown.rootSize

/**
 * JCEF 回落适配器（`kind = Jcef`），走**离屏渲染**。
 *
 * 只有 `MarkdownConfig.webFallback = WebFallback.Jcef` 时才会被注册；
 * 运行环境没有 `org.cef` 时 [isAvailable] 为 false，链路自动退回源码适配器 ——
 * **不需要 JBR 也能构建本库**。
 *
 * 流式期间不参与（`kind = Jcef` 在路由层被跳过），避免每帧重载浏览器内容。
 */
internal class JcefAdapter(private val config: MarkdownConfig) : BlockAdapter {

    override val id: String = "builtin.jcef"
    override val kind: AdapterKind = AdapterKind.Jcef
    override val priority: Int = 50

    override fun formats(): Set<FormatId> = setOf(Formats.Html, Formats.Math, Formats.Mermaid)

    override fun isAvailable(): Boolean = JcefRuntime.isPresent

    override fun canRender(block: MdBlock): Boolean = documentFor(block) != null

    @Composable
    override fun Render(block: MdBlock, modifier: Modifier) {
        val html = remember(block.key, block.raw) { documentFor(block) }
        if (html == null) {
            SourceBlock(block, modifier)
            return
        }
        if (LocalIsStreaming.current) {
            SourceBlock(block, modifier)
            return
        }

        var failure: String? by remember(block.key) { mutableStateOf(null) }
        var frame: ImageBitmap? by remember(block.key) { mutableStateOf(null) }
        val slot = remember(block.key) {
            CefSlot(
                onFrame = { bitmap -> javax.swing.SwingUtilities.invokeLater { frame = bitmap } },
                onError = { error ->
                    javax.swing.SwingUtilities.invokeLater {
                        failure = error.message ?: error::class.simpleName ?: "JCEF 初始化失败"
                    }
                    config.diagnostics.onFailure(id, block.key, error)
                },
            )
        }
        val density = LocalDensity.current

        SideEffect { JcefBudget.capacity = config.jcefPoolSize }

        // 名额由可见性决定：块在 onGloballyPositioned 里上报位置，JcefBudget 按距离分配。
        val granted = JcefBudget.isGranted(block.key)

        // 失去名额就释放浏览器（Chromium 进程），重新拿到时下面的 request 会重建。
        LaunchedEffect(granted) {
            if (!granted) {
                slot.releaseEngine()
                frame = null
            }
        }

        val message = failure
        if (message != null) {
            SourceBlock(block, modifier, message)
            return
        }

        // 槽位只随块创建/销毁；尺寸与内容变化不影响引擎存活。
        DisposableEffect(block.key) {
            onDispose {
                slot.dispose()
                JcefBudget.forget(block.key)
            }
        }

        BoxWithConstraints(
            modifier
                .fillMaxWidth()
                .height(config.webFallbackHeight)
                .onGloballyPositioned { coords ->
                    val rootHeight = coords.rootSize().height
                    if (rootHeight <= 0) return@onGloballyPositioned
                    val bounds = coords.boundsInRoot()
                    // LazyColumn 预组合但尚未测量的项 bounds 全零，不能当成「在视口中心」参与分配。
                    if (bounds.width <= 0f || bounds.height <= 0f) {
                        JcefBudget.report(block.key, null)
                        return@onGloballyPositioned
                    }
                    // 预取半屏：滚动时下一块能提前开始初始化，减少「等待中」的观感。
                    val margin = config.webFallbackHeight.value * density.density * 0.5f
                    val inRange = bounds.bottom > -margin && bounds.top < rootHeight + margin
                    if (inRange) {
                        val center = (bounds.top + bounds.bottom) / 2f
                        JcefBudget.report(block.key, kotlin.math.abs(center - rootHeight / 2f))
                    } else {
                        JcefBudget.report(block.key, null)
                    }
                },
        ) {
            val widthPx = with(density) { maxWidth.roundToPx() }
            val heightPx = with(density) { maxHeight.roundToPx() }

            if (!granted) {
                Text(
                    text = "等待浏览器实例（上限 ${config.jcefPoolSize}）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@BoxWithConstraints
            }

            LaunchedEffect(slot, widthPx, heightPx, html) {
                slot.request(widthPx, heightPx, html)
            }

            val bitmap = frame
            if (bitmap != null) {
                Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
            } else {
                Text(
                    text = "浏览器渲染中…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    /** 为块生成 HTML；返回 null 表示这个块不走回落渲染（交给源码兜底）。 */
    private fun documentFor(block: MdBlock): String? {
        config.webDocument?.invoke(block)?.let { return it }
        return when (block) {
            is MdBlock.Html -> if (config.allowRawHtml) wrap(block.raw) else null
            else -> null
        }
    }

    private fun wrap(body: String): String = """
        <!doctype html>
        <html><head><meta charset="utf-8"></head>
        <body style="margin:0;padding:12px;font:15px/1.6 system-ui,sans-serif;color:#1b1b1f;background:#fff">
        <h2 style="color:#c00;margin:0 0 8px">JCEF 已加载</h2>
        $body
        </body></html>
    """.trimIndent()
}

@Composable
private fun SourceBlock(block: MdBlock, modifier: Modifier, note: String? = null) {
    if (note != null) {
        Text(
            text = "回落渲染不可用：$note",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(bottom = 4.dp),
        )
    }
    Text(
        text = block.raw,
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = FontFamily.Monospace,
        modifier = modifier,
    )
}
