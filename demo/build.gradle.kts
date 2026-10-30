import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // 版本由根项目（Kotlin/Compose 插件已在 classpath）提供，这里不能带版本，
    // 否则 Gradle 会报 "plugin is already on the classpath with an unknown version"。
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_25) }
}

// 本机 JAVA_HOME 是 JDK 27，Java 任务默认跟到 27 会与 Kotlin 的 25 冲突。
tasks.withType<JavaCompile>().configureEach { options.release.set(25) }

dependencies {
    implementation(project(":"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
}

compose.desktop {
    application {
        mainClass = "io.github.shinglem.markdown.demo.MainKt"
        // 用 JBR with JCEF 跑 demo 时传 -PjbrHome=<JBR 路径>：
        // 它会切换到该 JDK 并加上 --add-modules jcef。普通 JDK 下不加，JCEF 适配器自动降级。
        val jbrHome = providers.gradleProperty("jbrHome").orNull
        if (jbrHome != null) {
            javaHome = jbrHome
            jvmArgs += listOf(
                "--add-modules", "jcef",
                // JDK 24+ 起 System.loadLibrary 需要显式授权（JCEF 在 native 初始化里加载 libcef/jcef）
                "--enable-native-access=jcef",
                // JCEF 内部依赖 sun.awt 的 peer 实现（KCEF 文档列出的必需项）
                "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
                "--add-opens", "java.desktop/java.awt.peer=ALL-UNNAMED",
            )
        }
    }
}
