// ============================================================================
//  markdown-one · Gradle 设置
//  ---------------------------------------------------------------------------
//  仓库是**独立的 public 构建**，同时也会被宿主工程以 git submodule 挂载
//  （宿主用 include(":modules:markdown-one")，本文件在宿主构建里不生效）。
//  因此：
//   · 只有本文件里的仓库列表对独立构建生效；
//   · 库脚本里不能引用宿主 toml 不存在的 version catalog 条目（见 README「构建契约」）。
//
//  仓库顺序：国内镜像在前、官方源兜底。CI 用 -Pmirror=off 只用官方源，
//  否则境外构建机会在每个构件上先撞国内镜像超时。
// ============================================================================

rootProject.name = "markdown-one"

pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://mirrors.cloud.tencent.com/nexus/repository/gradle-plugins/")
        maven("https://maven.aliyun.com/repository/public")
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    val useCnMirror = (providers.gradleProperty("mirror").orNull ?: "cn") != "off"

    repositories {
        if (useCnMirror) {
            // 腾讯云 maven-public 放第一：实测构件比阿里云完整
            maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public")
            maven("https://maven.aliyun.com/repository/public")
            maven("https://maven.aliyun.com/repository/google") {
                content {
                    includeGroupByRegex("androidx\\..*")
                    includeGroupByRegex("org\\.jetbrains\\.androidx\\..*")
                }
            }
        }
        mavenCentral()
        google()
    }
}

include(":demo")
