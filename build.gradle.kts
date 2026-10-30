// ============================================================================
//  markdown-one · 核心库（仓库根 = 库本体）
//  ---------------------------------------------------------------------------
//  · 被宿主工程以 submodule 挂载时，本脚本作为 :modules/markdown-one 子项目执行；
//    那时 `libs` 指向**宿主**的 version catalog，所以：
//      - 插件一律用 alias（借此强制与宿主 Kotlin/Compose 版本一致）；
//      - 依赖坐标写死在下方常量里，不引用 catalog 条目（宿主里没有 → 会直接报错）。
//  · target 名保持 `desktop`：宿主 composeApp 用的就是 jvm("desktop")，
//    同一构建内的 KMP project 依赖要求 target 名对齐。多端化时再一起改成 jvm()。
// ============================================================================

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
}

group = "io.github.shinglem"
version = "0.1.0-SNAPSHOT"

/** 依赖版本唯一处。改版本时同步 README「依赖」表。 */
val vMikepenz = "0.46.0"
val vIntellijMarkdown = "0.7.3"
val vHighlights = "1.1.0"
val vCoil = "3.6.3"
val vMermaidCmp = "0.1.21"

kotlin {
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_25) }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)

            // Markdown 解析（GFM AST）与块渲染的默认实现
            api("org.jetbrains:markdown:$vIntellijMarkdown")
            implementation("com.mikepenz:multiplatform-markdown-renderer:$vMikepenz")
            implementation("com.mikepenz:multiplatform-markdown-renderer-m3:$vMikepenz")
            implementation("com.mikepenz:multiplatform-markdown-renderer-code:$vMikepenz")
            // 图片块：core 的默认实现只有一个占位色块，真正加载图片要靠 -coil3 模块
            implementation("com.mikepenz:multiplatform-markdown-renderer-coil3:$vMikepenz")

            implementation("dev.snipme:highlights:$vHighlights")

            implementation("io.coil-kt.coil3:coil-compose:$vCoil")
            implementation("io.coil-kt.coil3:coil-svg:$vCoil")
            // 徽章、远程插图都是 http(s) URL：没有 network fetcher 时 Coil 一律加载失败
            implementation("io.coil-kt.coil3:coil-network-ktor3:$vCoil")

            // mermaid 纯 Kotlin 引擎（无 WebView），ADR 见 .design
            implementation("io.github.swithun-liu:mermaid-compose:$vMermaidCmp")
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(compose.runtime)
        }
    }
}
