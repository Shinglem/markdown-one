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
    }
}
