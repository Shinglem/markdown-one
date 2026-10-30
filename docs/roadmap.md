# 路线图

没有承诺的时间表，按优先级排序。想推动某一项就开 issue 讨论 ——
尤其是「某个渲染能力」这类需求，通常做成 `BlockAdapter` 就够了，不必等核心改动。

## 正在进行

- **发布到 Maven Central**：当前只能以 git submodule 或本地构建接入。
  需要补齐 `maven-publish` 的 POM、签名与 sources/javadoc artifact，见 [构建契约](../README.md#构建契约改动前务必先读)。

## 接下来

### 1. 请求拦截（安全）

JCEF 页面里的外链请求目前不受限，`<img src="http://tracker/...">` 会被真的请求出去。
计划在 `CefEngine` 创建 client 时挂 `CefRequestHandler` 代理，
`onBeforeResourceLoad` 里按宿主提供的白名单过滤，默认只放行 `data:`。

在此之前，宿主应按 [SECURITY](../SECURITY.md#jcef-是攻击面) 里的建议自己收紧。

### 2. 进程退出兜底

用 JCEF 时关闭窗口后 JVM 可能不退出（CEF 全局实例的非 daemon 线程）。
计划提供 `CefBootstrap.shutdown()` 与一个可选的「关窗后兜底退出」开关，
而不是库自己悄悄 `exitProcess`。

### 3. 更多原生适配器

| 适配器 | 说明 |
|---|---|
| `math` | KaTeX 走浏览器是现成路径；纯 Compose 排版需要自己写解析器，优先级低 |
| `image` | 当前用 Coil，缺尺寸约束与失败占位的一致性处理 |
| 表格冻结表头 | 视口内长表格固定表头，纯 Compose 可做 |

### 4. Android target

结构上已预留（`commonMain` + 两个 `expect`），需要：

- `androidMain` 的 `discoverAdapterFactories()`（返回空或反射扫 dex）与 `platformAdapters()`；
- `BlockScrollbarOverlay` 的 Android 版（触屏可以画空）；
- Compose Desktop 专有 API 的替换（`HorizontalScrollbar` / `VerticalScrollbar` 是 desktop 的）。

### 5. Web target（Compose for Web / Wasm）

解析层与适配器 SPI 都能编译，主要工作是：

- 去掉 `desktopMain` 的 `ServiceLoader` 依赖；
- 图片（Coil 3 有 web 支持）与代码高亮的等价实现；
- 大块视口的滚动条改为平台习惯（不画）。

## 不会做

| 不做 | 原因 |
|---|---|
| WYSIWYG 编辑器 | 本库只渲染，编辑是另一件事；输入框要自己接管 IME |
| 内嵌 JS 插件宿主 | 前端插件返回 `ReactNode`，保留生态等于自造 WebView |
| 多语言 UI 文案 | 当前只有中文，文案集中在 `MarkdownConfig` |
| 把整篇文档交给一个 WebView | 失去块级降级与流式稳定性，本库存在的理由就没了 |
