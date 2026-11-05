package io.github.shinglem.markdown.text

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter

/**
 * 图片全屏预览。
 *
 * 为什么需要：AI 常贴报错截图/架构图，正文里被压到可用宽度后完全看不清细节。
 * 点击任意图片（行内或块级）进来，按原始比例铺满窗口，点空白处或按关闭退出。
 *
 * 用 `usePlatformDefaultWidth = false` 让 Dialog 真的铺满——默认宽度只有 560dp，
 * 放大图片毫无意义。
 */
@Composable
internal fun MdImagePreview(url: String, alt: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val painter = rememberAsyncImagePainter(model = url)
        val state by painter.state.collectAsState()
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black.copy(alpha = 0.94f),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // 点背景关闭：不用 clickable（会带涟漪与语义），也不吃掉图片自身的点击
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } },
                contentAlignment = Alignment.Center,
            ) {
                when (state) {
                    is AsyncImagePainter.State.Loading, AsyncImagePainter.State.Empty -> {
                        CircularProgressIndicator(color = Color.White)
                    }

                    is AsyncImagePainter.State.Error -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("图片加载失败", color = Color.White)
                            Text(
                                text = url,
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }

                    else -> {
                        Image(
                            painter = painter,
                            contentDescription = alt.ifBlank { null },
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp)
                                // 点图片本身不关闭（否则想细看时会误关）
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {},
                                ),
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    alt.takeIf { it.isNotBlank() }?.let { label ->
                        Text(
                            text = label,
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(end = 4.dp, top = 10.dp),
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text("关闭", color = Color.White)
                    }
                }
            }
        }
    }
}
