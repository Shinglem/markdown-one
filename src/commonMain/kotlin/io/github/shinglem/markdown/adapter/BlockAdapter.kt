package io.github.shinglem.markdown.adapter

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.shinglem.markdown.model.MdBlock

public enum class AdapterKind {
    /** 纯 Compose 绘制。 */
    Native,

    /** 交给内嵌浏览器（JCEF）离屏渲染。 */
    Jcef,

    /** 最后一级兜底：显示源码。 */
    Fallback,
}

/**
 * 块渲染适配器。
 *
 * 一个适配器可以声明多种格式（[formats]），例如同时支持 `mermaid` 与 `code:mermaid`。
 *
 * 路由规则：候选 = 声明格式与块的 [MdBlock.formats] 有交集
 * → 过滤 [isAvailable] 与 [canRender] → 按 [priority] 降序取第一个 → 全落空走兜底。
 *
 * 两条不可违反的纪律：
 * 1. **禁止静默替换**：画不出时必须走下一级，不能悄悄换个引擎顶替；
 * 2. **流式只降不升**：流式期间 JCEF 适配器不参与（见 [LocalIsStreaming]）。
 */
public interface BlockAdapter {
    /** 唯一标识，用于配置里禁用/覆盖。 */
    public val id: String

    public val kind: AdapterKind

    /** 越大越优先。约定：Native 100 / Jcef 50 / Fallback 0。 */
    public val priority: Int

    /** 本适配器支持的格式。 */
    public fun formats(): Set<FormatId>

    /** 运行期探测（例如 JCEF 是否可初始化）。结果应缓存，不要每次渲染都探测。 */
    public fun isAvailable(): Boolean

    /** 比格式声明更细的判定；默认放行。 */
    public fun canRender(block: MdBlock): Boolean

    @Composable
    public fun Render(block: MdBlock, modifier: Modifier)
}

/** 由可选 artifact 通过 `META-INF/services` 提供。 */
public fun interface BlockAdapterFactory {
    public fun create(): List<BlockAdapter>
}

/** 路由诊断：选择结果与失败都从这里出去，供 demo 面板与宿主日志使用。 */
public interface AdapterDiagnostics {
    public fun onRoute(blockKey: String, formats: Set<FormatId>, adapterId: String?, kind: AdapterKind?)

    public fun onFailure(adapterId: String, blockKey: String, error: Throwable?)

    public object None : AdapterDiagnostics {
        override fun onRoute(blockKey: String, formats: Set<FormatId>, adapterId: String?, kind: AdapterKind?) {}
        override fun onFailure(adapterId: String, blockKey: String, error: Throwable?) {}
    }
}
