package com.study.snapinput.core.config

/**
 * 读取编写期原始配置 JSON 文本（layout / theme / template）。
 *
 * 实现可来自 assets、文件或测试桩。仅负责读取「原始文本」，不做反序列化与展开；
 * 反序列化由 [KeyboardConfigParser]、展开由 KeyboardConfigResolver 负责。
 */
interface ConfigLoader {
    /**
     * 读取 Active_Layout 的 Layout 文件、其引用的 Theme 文件与全部 Template 文件的原始 JSON 文本。
     *
     * 共享的 theme/template 会被缓存以避免重复读取；任一文件缺失/不可读，或全部读取未在 2 秒内完成，
     * 返回携带原因（如 [Reason.MISSING_REFERENCED_FILE]）的 [LoadResult.Failure]。
     *
     * @param layoutId 目标布局 id（本轮固定为 "en"）。
     */
    suspend fun loadAuthoringSources(layoutId: String): LoadResult
}

/**
 * 一次加载得到的全部编写期原始文本。
 *
 * @property layoutId 布局 id。
 * @property layoutJson 布局文件原始 JSON 文本。
 * @property themeId 布局所引用的主题 id。
 * @property themeJson 主题文件原始 JSON 文本。
 * @property templates 模板名 -> 模板文件原始 JSON 文本（按 layout.templates 顺序）。
 */
data class AuthoringSources(
    val layoutId: String,
    val layoutJson: String,
    val themeId: String,
    val themeJson: String,
    val templates: Map<String, String>
)

/**
 * 加载结果。
 *
 * - [Success]：读取到全部编写期原始文本。
 * - [Failure]：任一文件缺失/不可读，或读取超时，携带原因。
 */
sealed interface LoadResult {
    /** 加载成功。 */
    data class Success(val sources: AuthoringSources) : LoadResult
    /** 加载失败，原因见 [error]（如 [Reason.MISSING_REFERENCED_FILE] / 超时 / IO）。 */
    data class Failure(val error: ConfigError) : LoadResult
}
