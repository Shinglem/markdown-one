# 安全策略

## 报告漏洞

**不要开公开 issue。** 用 GitHub 的
[私密漏洞报告](https://github.com/Shinglem/markdown-one/security/advisories/new)，
或在 issue 里只写「想报告安全问题」，维护者会建立私密通道。

请附上：影响的 commit、复现用的 Markdown 片段、以及你认为的攻击面
（例如「这段 Markdown 能让库去访问内网地址」）。

## 威胁模型

本库的核心用途是渲染**不可信输入**（LLM 输出、用户粘贴的文档、外部 API 返回的 Markdown），
但**不执行任何脚本**：没有 WebView、没有 Chromium、没有 JS 引擎。下面这些边界是刻意的，
相关改动请按安全改动对待。

### 默认不执行任何东西

| 输入 | 行为 |
|---|---|
| 原始 HTML 块 | 按白名单**原生渲染**（ksoup 解析 → Compose 块树）：`script` / `style` / `noscript` / `iframe` 子树整棵丢弃，`on*` 与 `class` 属性丢弃 |
| 数学公式 | `ratex` 原生排版，只接受 KaTeX 语法子集，不加载任何脚本 |
| 图表 | `diagram-render` 原生解析与排版，不加载任何脚本 |
| 链接 | 只回调 `onLinkClick`，库不打开任何东西 |
| 图片 | Coil 拉取，**不做域名白名单** |

### HTML 白名单

元素与属性白名单照 GitHub `html-pipeline` 的 `sanitization_filter.rb`（见
`parse/HtmlSanitizer.kt`）：

- 元素白名单 60 个；不在名单里的标签**剥壳保留内容**，不会连正文一起吞掉；
- 属性白名单里**没有 `style`、没有 `class`、没有 `on*`**；
- `style` 只解析能映射到 `TextStyle` 的 6 个属性（`color` / `background-color` /
  `font-size` / `font-weight` / `font-style` / `text-align`），
  `display` / `flex` / `grid` / `position` / `float` / `margin` / `padding` 等布局属性**静默忽略**；
- 防护：嵌套深度上限 32、节点数上限 5000，超限整块回落源码。

### 剩下的风险与建议

| 风险 | 说明与处置 |
|---|---|
| 外链图片（追踪像素 / SSRF） | `![x](http://169.254.169.254/...)` 会被 Coil 真的请求。需要严格控制时，由宿主给图片域名加白名单或直接禁用网络图片 |
| 超大输入 | HTML 有节点上限；代码块超过 `maxSyncHighlightLength` 跳过同步着色，避免卡 UI |
| 诊断回调 | `blockKey` 与错误信息可能带上块内容，宿主记录日志时注意不要连带用户内容外泄 |

### 其它

- **不写文件**：库不落盘（native 库由 `ratex-native-*` 自己解压到临时目录）。
- **不联网**：除图片加载外，库不发起网络请求。

## 支持的版本

当前只有 `main` 分支，安全修复直接进 `main`。
