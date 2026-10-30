package io.github.shinglem.markdown.parse

import io.github.shinglem.markdown.model.MdDocument

/** 把 Markdown 源码解析成块序列。 */
public interface MarkdownParser {
    public fun parse(source: String, streaming: Boolean): MdDocument
}
