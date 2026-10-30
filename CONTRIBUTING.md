# 贡献指南

感谢参与。本仓库的目标是「一个可替换渲染器的 Markdown 组件」，改动前请先读
[架构](docs/architecture.md) —— 尤其是「两条纪律」那一节，它们决定了哪些改法会被拒。

## 环境

| 项 | 版本 |
|---|---|
| JDK | 25（`JAVA_HOME` 指向它；本机若是更高版本，Gradle 任务已固定 `release = 25`） |
| Gradle | 9.8.0（用仓库里的 wrapper，不要用本机 Gradle） |
| Kotlin / Compose | 见 `gradle/libs.versions.toml`，不要手动改版本号 |

JCEF 相关开发额外需要 **JBR with JCEF**（`jbrsdk_jcef-*`），普通 JDK 也能构建，只是跑不了浏览器回落。

## 常用命令

```bash
./gradlew :desktopTest              # 单元测试（唯一的自动化验证）
./gradlew :demo:run                 # 交互式 sandbox
./gradlew :demo:run -Pfixture=0     # 直接进某个 fixture（序号或名称）
./gradlew build                     # 全量构建
```

国内网络默认走镜像；CI 用 `-Pmirror=off` 只连官方源。改构建脚本时别把镜像地址写死。

## 自检清单

提 PR 之前请确认：

- [ ] `./gradlew :desktopTest` 通过
- [ ] `./gradlew :demo:run` 里相关 fixture 目视正常（右侧路由面板没有意外变化）
- [ ] 新写的文件一律 **LF** 行尾（仓库 `.gitattributes` 已强制，但别让编辑器偷偷改）
- [ ] 公开 API 有 KDoc，且说明了「为什么」而不是重复签名
- [ ] 涉及行为变化时同步更新 `README.md` / `docs/` 与 `CHANGELOG.md`

## 代码风格

- Kotlin 官方风格，4 空格缩进，文件名 = 顶层声明名。
- `public` 声明显式写修饰符（本仓库按 explicit API 风格维护）。
- 组合函数大驼峰 + `@Composable`；非组合函数小写驼峰。
- **注释只写「为什么」**：一段代码如果有非显然的原因（绕过的坑、平台限制、实测结论），
  必须写清现象与出处；一眼能看懂的代码不要加注释。
- 不引入新的状态管理框架（redux 式、响应式缓存），`StateFlow` / `MutableStateFlow` 够用。
- 新增依赖前先确认坐标在 Maven Central 可查、许可证兼容 Apache-2.0，并同步 README 的依赖表。

## 提交信息

```
<类型>: <简述>
```

类型取 `feat` / `fix` / `refactor` / `docs` / `chore` / `test`。

- 一次提交一件事，不要混「重构 + 功能」；
- 涉及设计取舍的改动写进 `docs/` 或 commit body，别只留在 PR 描述里；
- 行为修复请写清**现象 → 根因 → 改法**，这个仓库的每个坑都是这么留下来的。

## 加一个适配器

最常见的外部贡献。步骤见 [写适配器](docs/adapters.md)，要点：

1. `id` 别和内置撞（`builtin.*`）；
2. 想接管内置实现就把 `priority` 抬到 100 以上；
3. `isAvailable()` 的结果要缓存；
4. 画不出来必须自己降级并留下可见痕迹（源码 + 提示），不能返回空。

如果适配器依赖重（KaTeX、原生渲染器），推荐做成独立 artifact + `META-INF/services`
自动发现，而不是往核心库里塞依赖。

## 加一个 target

1. 在 `build.gradle.kts` 的 `kotlin {}` 里加 target；
2. 给两个 `expect` 函数写 `actual`：
   - `discoverAdapterFactories()` —— 没有 ServiceLoader 的平台返回 `emptyList()`；
   - `platformAdapters(config)` —— 平台专有适配器，没有就返回 `emptyList()`；
3. 补平台专有实现（滚动条 `BlockScrollbarOverlay` 已经是 `expect/actual`，其它端可以画空）；
4. 在 `demo` 里加对应的运行目标，并在 README 的「状态」表打勾。

**注意 `desktop` target 的名字不能改**：宿主工程用 `jvm("desktop")` 挂在同一构建里，
KMP project 依赖要求 target 名对齐。多端化时要和宿主一起改。

## 报问题

用 issue 模板，附上：

- 库版本（commit）与 JDK/JBR 版本；
- 触发问题的 Markdown 片段（**最小复现**，越短越好）；
- `AdapterDiagnostics` 的路由输出，或 demo 右侧面板的截图；
- 如果是 JCEF 相关，贴出 CEF 的 native 日志（`cache_path` 目录下的 `*.log`）。

**安全问题不要开公开 issue**，走 [SECURITY.md](SECURITY.md) 的私密通道。

## 许可证

提交即表示同意以 [Apache-2.0](LICENSE) 授权你的贡献。
