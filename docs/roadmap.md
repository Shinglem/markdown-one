# 路线图

没有承诺的时间表，按优先级排序。想推动某一项就开 issue 讨论 ——
尤其是「某个渲染能力」这类需求，通常做成 `BlockAdapter` 就够了，不必等核心改动。

## 正在进行

- **发布到 Maven Central**：当前只能以 git submodule 或本地构建接入。
  需要补齐 `maven-publish` 的 POM、签名与 sources/javadoc artifact，见 [构建契约](../README.md#构建契约改动前务必先读)。

## 接下来

### 1. 图片策略

当前用 Coil，缺「尺寸约束与失败占位的一致性处理」：`<img width/height>` 已能透传，
Markdown 图片还没有等价写法（现在用 title 里的 `size=WxH` 兜着）。
计划统一成一套占位/失败/最大尺寸规则，并给宿主一个「是否允许网络图片」的开关。

### 2. 表格冻结表头与列宽拖拽

视口内长表格固定表头，纯 Compose 可做；列宽拖拽需要把「按权重均分」换成可覆盖的列宽状态。

### 3. 更多原生适配器

| 适配器 | 说明 |
|---|---|
| 代码行号 / 行高亮 | 等内置编辑器的决策，能复用同一套高亮结果 |
| ANSI 着色 | 终端输出粘进对话时有用 |
| Emoji 短代码 | `:smile:` 这类 GitHub 短代码 |

### 4. Android target

结构上已预留（`commonMain` + 两个 `expect`），需要：

- `androidMain` 的 `discoverAdapterFactories()`（返回空或反射扫 dex）与 `platformAdapters()`；
- `BlockScrollbarOverlay` 的 Android 版（触屏可以画空）；
- Compose Desktop 专有 API 的替换（`HorizontalScrollbar` / `VerticalScrollbar` 是 desktop 的）；
- 公式引擎换 Android 版 `ratex` native（或退回源码）。

### 5. Web target（Compose for Web / Wasm）

解析层与适配器 SPI 都能编译，主要工作是：

- 去掉 `desktopMain` 的 `ServiceLoader` 依赖；
- 图片（Coil 3 有 web 支持）与代码高亮的等价实现；
- 大块视口的滚动条改为平台习惯（不画）。

## 不会做

| 不做 | 原因 |
|---|---|
| 内嵌浏览器 / JCEF | 已移除：310 MB native、渲染进程、脚本攻击面，换来的只是「任意 HTML 完美渲染」，而对话里实际出现的 HTML 都是标签语义级别，原生渲染已覆盖 |
| WYSIWYG 编辑器 | 本库只渲染，编辑是另一件事；输入框要自己接管 IME |
| 内嵌 JS 插件宿主 | 前端插件返回 `ReactNode`，保留生态等于自造 WebView |
| 多语言 UI 文案 | 当前只有中文，文案集中在 `MarkdownConfig` |
| 把整篇文档交给一个 WebView | 失去块级降级与流式稳定性，本库存在的理由就没了 |
