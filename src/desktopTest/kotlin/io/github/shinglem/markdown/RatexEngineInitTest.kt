package io.github.shinglem.markdown

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 诊断：`io.ratex.RaTeXEngine` 的静态初始化在哪一步失败。
 *
 * 生产路径用 `runCatching` 吞掉了首次异常，后续只能看到
 * `NoClassDefFoundError: Could not initialize class`，拿不到真正原因。
 *
 * ⚠️ **本机跑这条测试前先退出在运行的客户端**：ratex 把 native dll 解压到
 * `%LOCALAPPDATA%\RaTeX\jna\windows-<arch>\ratex_ffi.dll`，解压是「先删后拷」；
 * 客户端正在运行时会锁住那个文件，于是这条测试失败，与本库无关。
 */
class RatexEngineInitTest {

    @Test
    fun `引擎可以初始化`() {
        val err = runCatching { Class.forName("io.ratex.RaTeXEngine") }.exceptionOrNull()
        val cause = err?.cause ?: err
        assertTrue(
            err == null,
            "初始化失败：$err\n--- cause ---\n${cause?.stackTraceToString()?.take(2000)}",
        )
    }
}
