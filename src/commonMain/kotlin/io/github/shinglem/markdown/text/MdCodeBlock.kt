package io.github.shinglem.markdown.text

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shinglem.markdown.code.highlightCode
import kotlinx.coroutines.delay

/**
 * 代码块外壳：语言标签 + 复制按钮 + 等宽正文。
 *
 * 同步着色而不是异步：逐帧异步会出现「先无色、下一帧才上色」的闪烁。
 * 超过 [maxSyncHighlightLength] 的块直接纯文本，避免卡住 UI。
 *
 * 这个组件同时供 `builtin.code` 适配器与「引用/列表项里的代码块」使用，
 * 保证同一份代码在哪儿画都一样。
 */
@Composable
internal fun MdCodeBlock(
    code: String,
    language: String?,
    modifier: Modifier = Modifier,
    maxSyncHighlightLength: Int = LocalMdMaxSyncHighlightLength.current,
) {
    val colors = LocalMdColors.current
    val dimens = LocalMdDimens.current
    val padding = LocalMdPadding.current
    val typography = LocalMdTypography.current
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val highlighted = remember(code, language, dark, maxSyncHighlightLength) {
        if (code.length <= maxSyncHighlightLength) {
            highlightCode(code, language, dark)
        } else {
            AnnotatedString(code)
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(dimens.codeBackgroundCornerSize))
            .background(colors.codeBackground),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                // 与既有观感对齐：语言标签统一大写（宿主快照/测试按 `KOTLIN` 断言）。
                text = language?.trim()?.takeIf { it.isNotBlank() }?.uppercase() ?: "",
                style = typography.code.copy(fontSize = 12.sp),
                color = colors.text.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            CopyCodeButton(code)
        }
        // 顶栏与代码之间的分隔线：0.5dp 在 2x 屏上才不会被压成 0 像素
        Spacer(Modifier.fillMaxWidth().height(0.5.dp).background(colors.dividerColor.copy(alpha = 0.3f)))
        BasicText(
            text = highlighted,
            style = typography.code,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(padding.codeBlock),
        )
    }
}

@Suppress("DEPRECATION")
@Composable
private fun CopyCodeButton(code: String) {
    val clipboard = LocalClipboardManager.current
    val colors = LocalMdColors.current
    val typography = LocalMdTypography.current
    var copied by remember(code) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_200)
            copied = false
        }
    }
    Text(
        text = if (copied) "已复制" else "复制",
        style = typography.code.copy(fontSize = 11.sp),
        color = colors.text.copy(alpha = 0.7f),
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable {
                clipboard.setText(AnnotatedString(code))
                copied = true
            }
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
