package io.github.shinglem.markdown

/**
 * 「原生渲染做不了」时的回落策略。
 *
 * - [Source]：直接显示源码（默认，零依赖，永远可用）。
 * - [Jcef]：交给内嵌浏览器渲染。**运行期反射发现** `org.cef.*`，
 *   找不到就自动退回 [Source] —— 库本身**不依赖** JCEF，也不需要 JBR 才能构建。
 */
public sealed interface WebFallback {
    public data object Source : WebFallback
    public data object Jcef : WebFallback
}
