package io.github.shinglem.markdown.web

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CefNativeLocatorTest {

    private val savedExplicit = CefNativeLocator.explicitDir
    private val savedProp = System.getProperty(CefNativeLocator.SYSPROP)
    private val tempDirs = mutableListOf<File>()

    @AfterTest
    fun restore() {
        CefNativeLocator.explicitDir = savedExplicit
        if (savedProp == null) {
            System.clearProperty(CefNativeLocator.SYSPROP)
        } else {
            System.setProperty(CefNativeLocator.SYSPROP, savedProp)
        }
        tempDirs.forEach { it.deleteRecursively() }
    }

    @Test
    fun `empty dir has no native`() {
        assertFalse(CefNativeLocator.containsNative(tempDir()))
    }

    @Test
    fun `helper alone marks dir as a cef runtime`() {
        val dir = tempDir()
        // helper 是 CEF 子进程宿主，任何发行包都带；只认共享库会漏掉 macOS 的 framework 布局。
        File(dir, "jcef_helper.exe").writeText("")
        assertTrue(CefNativeLocator.containsNative(dir))
    }

    @Test
    fun `libcef is listed before jcef`() {
        val dir = tempDir()
        val libcef = File(dir, CefNativeLocator.libFileNames("libcef").first())
        val jcef = File(dir, CefNativeLocator.libFileNames("jcef").first())
        libcef.writeText("")
        jcef.writeText("")
        assertEquals(listOf(libcef, jcef), CefNativeLocator.nativeLibs(dir))
    }

    @Test
    fun `explicit dir wins over system property`() {
        val explicit = cefDir()
        val fromProp = cefDir()
        CefNativeLocator.explicitDir = explicit
        System.setProperty(CefNativeLocator.SYSPROP, fromProp.absolutePath)
        assertEquals(explicit, CefNativeLocator.resolve())
    }

    @Test
    fun `explicit dir without native falls through to system property`() {
        val explicit = tempDir()
        val fromProp = cefDir()
        CefNativeLocator.explicitDir = explicit
        System.setProperty(CefNativeLocator.SYSPROP, fromProp.absolutePath)
        assertEquals(fromProp, CefNativeLocator.resolve())
    }

    @Test
    fun `candidates are deduplicated`() {
        val dir = cefDir()
        CefNativeLocator.explicitDir = dir
        System.setProperty(CefNativeLocator.SYSPROP, dir.absolutePath)
        assertEquals(1, CefNativeLocator.candidates().count { it == dir })
    }

    private fun tempDir(): File =
        Files.createTempDirectory("cef-locator").toFile().also { tempDirs += it }

    /** 造一个「看起来像 CEF 运行时」的目录。 */
    private fun cefDir(): File = tempDir().also { File(it, "jcef_helper.exe").writeText("") }
}
