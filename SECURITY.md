# 安全策略

## 报告漏洞

**不要开公开 issue。** 用 GitHub 的
[私密漏洞报告](https://github.com/Shinglem/markdown-one/security/advisories/new)，
或在 issue 里只写「想报告安全问题」，维护者会建立私密通道。

请附上：影响的 commit、复现用的 Markdown 片段、以及你认为的攻击面
（例如「这段 Markdown 能让浏览器访问内网地址」）。

## 威胁模型

本库的核心用途是渲染**不可信输入**（LLM 输出、用户粘贴的文档、外部 API 返回的 Markdown）。
所以下面这些边界是刻意的，相关改动请按安全改动对待。

### 默认不执行任何东西

| 输入 | 默认行为 | 打开后 |
|---|---|---|
| 原始 HTML 块 | 显示源码 | `allowRawHtml = true` 才交给浏览器 |
| 数学公式 / 图表脚本 | 不加载（库不带 KaTeX / mermaid.js） | 由调用方在 `webDocument` 里注入页面 |
| 链接 | 只回调 `onLinkClick`，库不打开任何东西 | 由宿主决定怎么打开 |
| 图片 | Coil 拉取，**不做域名白名单** | — |

### JCEF 是攻击面

一旦 `webFallback = Jcef` 且 `allowRawHtml = true`，不可信 Markdown 就进了 Chromium。
库自身做了这些限制：

- 每个块一个独立浏览器，块之间不共享 session 之外的任何东西；
- 内容通过 `data:text/html;base64,...` 加载，不写到文件、不走网络；
- 浏览器不可交互（没有输入事件转发），只出帧。

**库没有做、需要宿主自己做的**：

| 风险 | 说明 |
|---|---|
| 外链资源（追踪像素 / SSRF） | `<img src="http://169.254.169.254/...">` 会被浏览器真的去请求。需要挂 `CefRequestHandler.onBeforeResourceLoad` 做域名白名单，只放行 `data:` 与你自己的域 |
| 脚本执行 | `data:` 页面里的 `<script>` 会执行。要么在 HTML 里加 CSP（`default-src 'none'; img-src data: https://your-cdn`），要么关闭 `allowRawHtml` |
| 内存耗尽 | 单个巨型 HTML 块可以吃掉大量内存；`jcefPoolSize` 只限制实例数，不限制单页复杂度 |

推荐的稳妥配置：

```kotlin
MarkdownConfig(
    webFallback = WebFallback.Jcef,
    allowRawHtml = false,                  // 不给任意 HTML 进浏览器的机会
    webDocument = { block ->
        when (block) {
            is MdBlock.Math -> katexPage(block.tex)   // 自己控制页面，带 CSP
            else -> null
        }
    },
)
```

### 其它

- **不写文件**：库除 JCEF 的 cache 目录（按 PID 隔离）外不落盘。
- **不联网**：除图片加载与 JCEF 页面自身请求外，库不发起网络请求。
- **诊断回调**可能带上块内容（`blockKey` 与错误信息），宿主记录日志时注意不要连带用户内容外泄。

## 支持的版本

当前只有 `main` 分支，安全修复直接进 `main`。
