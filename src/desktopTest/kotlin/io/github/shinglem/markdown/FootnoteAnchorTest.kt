package io.github.shinglem.markdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.LinkAnnotation
import io.github.shinglem.markdown.parse.GfmMarkdownParser
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 脚注引用必须变成**可点击的文内锚点**。
 *
 * 真机反馈：「目录能跳，点脚注编号没反应」。目录项与脚注引用走的是同一条
 * `#fragment` → `onAnchorScroll` 链路，所以要么脚注引用根本没生成 link annotation，
 * 要么 `#fn-<id>` 解析不到目标块。这里先钉住前半段。
 */
@OptIn(ExperimentalTestApi::class)
class FootnoteAnchorTest {

    @Test
    fun `脚注引用渲染成指向 fn 锚点的链接`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MarkdownText("正文引用[^1]，还有第二个[^note]。\n\n[^1]: 第一条脚注。\n[^note]: 第二条脚注。")
            }
        }
        waitForIdle()
        val urls = collectLinkUrls()
        assertTrue(urls.any { it == "#fn-1" }, "缺少 #fn-1 链接，实际=$urls")
        assertTrue(urls.any { it == "#fn-note" }, "缺少 #fn-note 链接，实际=$urls")
    }

    @Test
    fun `目录项与脚注引用使用同一套锚点格式`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MarkdownText("## 目录\n\n- [跳到第二节](#二节)\n\n正文[^1]\n\n[^1]: 脚注。")
            }
        }
        waitForIdle()
        val urls = collectLinkUrls()
        assertTrue(urls.contains("#二节"), "目录链接缺失，实际=$urls")
        assertTrue(urls.any { it.startsWith("#fn-") }, "脚注链接缺失，实际=$urls")
    }

    /**
     * 锚点解析：`#fn-1` 必须落到**脚注区那一块**。目录能跳而脚注不能跳时，问题就在这一步。
     */
    @Test
    fun `fn 片段解析到脚注块`() {
        val doc = GfmMarkdownParser().parse("正文[^1]\n\n[^1]: 脚注内容。", streaming = false)
        val anchorOf = doc.blocks.mapNotNull { block -> anchorIdOf(block)?.let { anchor -> block.key to anchor } }.toMap()
        val anchorKeys = anchorOf.entries.associate { (key, anchor) -> anchor to key }
        assertTrue(anchorOf.isNotEmpty(), "文档里没有可用的锚点块，blocks=${doc.blocks.map { it::class.simpleName }}")
        assertTrue(
            resolveAnchorKey("fn-1", anchorKeys) != null,
            "fn-1 解析不到目标；anchorOf=$anchorOf",
        )
    }

    private fun androidx.compose.ui.test.ComposeUiTest.collectLinkUrls(): List<String> {
        val out = mutableListOf<String>()
        fun walk(node: SemanticsNode) {
            node.config.getOrNull(SemanticsProperties.Text)?.forEach { annotated ->
                annotated.getLinkAnnotations(0, annotated.length).forEach { range ->
                    (range.item as? LinkAnnotation.Url)?.let { out += it.url }
                }
            }
            node.children.forEach(::walk)
        }
        walk(onRoot().fetchSemanticsNode())
        return out
    }
}
