package io.github.shinglem.markdown.text

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.isSpecified
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
    /** ```` ```kotlin {1,3-5} ```` 里要高亮的行号（1 基）。 */
    highlightLines: Set<Int> = emptySet(),
    /** 是否有行号：只有多行代码才值得占一列。 */
    showLineNumbers: Boolean = true,
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
    val lines = remember(code) { code.lines() }
    val gutter = showLineNumbers && lines.size > 1
    // 行高必须固定成同一个值：行号列与代码列各画各的，靠它对齐。
    val lineHeight = typography.code.lineHeight
    val lineHeightDp = if (lineHeight.isSpecified) {
        with(LocalDensity.current) { lineHeight.toDp() }
    } else {
        with(LocalDensity.current) { typography.code.fontSize.toDp() * 1.6f }
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
        Row(
            Modifier
                .fillMaxWidth()
                // 整行高亮画在**这一层**：它横跨行号列与代码列，且不随代码的横向滚动移动
                .drawBehind {
                    if (highlightLines.isEmpty()) return@drawBehind
                    val rowHeight = lineHeightDp.toPx()
                    val top = padding.codeBlock.calculateTopPadding().toPx()
                    highlightLines.forEach { line ->
                        val index = line - 1
                        if (index < 0 || index >= lines.size) return@forEach
                        drawRect(
                            color = colors.highlightBackground,
                            topLeft = Offset(0f, top + index * rowHeight),
                            size = Size(size.width, rowHeight),
                        )
                    }
                    // 高亮行左侧的强调条（与 GitHub 的观感一致）
                    highlightLines.forEach { line ->
                        val index = line - 1
                        if (index < 0 || index >= lines.size) return@forEach
                        drawRect(
                            color = colors.highlightText.copy(alpha = 0.55f),
                            topLeft = Offset(0f, top + index * rowHeight),
                            size = Size(3.dp.toPx(), rowHeight),
                        )
                    }
                },
        ) {
            if (gutter) {
                LineNumberGutter(
                    count = lines.size,
                    lineHeight = lineHeightDp,
                    topPadding = padding.codeBlock.calculateTopPadding(),
                    bottomPadding = padding.codeBlock.calculateBottomPadding(),
                    highlightLines = highlightLines,
                )
            }
            BasicText(
                text = highlighted,
                style = typography.code,
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .padding(padding.codeBlock),
            )
        }
    }
}

/**
 * 行号列。
 *
 * 宽度按最大行号的位数算，避免四位数行号把代码挤走；每行高度显式指定为 [lineHeight]，
 * 与代码列用同一个 `lineHeight` 才可能对齐（代码块不软换行，所以一个逻辑行 = 一个物理行）。
 */
@Composable
private fun LineNumberGutter(
    count: Int,
    lineHeight: androidx.compose.ui.unit.Dp,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    highlightLines: Set<Int>,
) {
    val typography = LocalMdTypography.current
    val colors = LocalMdColors.current
    val density = LocalDensity.current
    val digits = count.toString().length
    val width = with(density) { typography.code.fontSize.toDp() * (digits * 0.75f + 1.2f) }
    Column(
        Modifier
            .padding(start = 8.dp, top = topPadding, bottom = bottomPadding)
            .width(width),
    ) {
        repeat(count) { index ->
            val lineNumber = index + 1
            Box(
                Modifier.fillMaxWidth().height(lineHeight),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = lineNumber.toString(),
                    style = typography.code,
                    color = if (lineNumber in highlightLines) {
                        colors.text.copy(alpha = 0.95f)
                    } else {
                        colors.text.copy(alpha = 0.38f)
                    },
                    maxLines = 1,
                )
            }
        }
        if (bottomPadding > 0.dp) Spacer(Modifier.height(0.dp))
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
