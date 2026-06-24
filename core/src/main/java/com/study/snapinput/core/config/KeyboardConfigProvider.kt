package com.study.snapinput.core.config

import com.study.snapinput.core.config.model.KeyboardConfig

/**
 * 一次配置加载得到的「生效配置」结果。
 *
 * @property config 当前生效的扁平运行时配置：成功时为 active layout 的展开产物，
 *   回退时为 [lastValid] 或 [DefaultConfig.config]。
 * @property usingFallback 是否处于回退状态（加载/反序列化/展开/校验任一步失败）。
 * @property errors 本轮编排过程中遇到的错误（成功时为空列表）。
 */
data class ActiveConfig(
    val config: KeyboardConfig,
    val usingFallback: Boolean,
    val errors: List<ConfigError> = emptyList()
)

/**
 * 配置加载编排器（core）。
 *
 * 串联 [ConfigLoader] → [KeyboardConfigParser.parseAuthoring] → [KeyboardConfigResolver.resolve]
 * → [KeyboardConfigValidator.validate]，把 active layout 的 authoring 源确定性地转换为生效的
 * 扁平 [KeyboardConfig]。任一步失败则回退到 [lastValid]（若非空）或内置 [DefaultConfig.config]，
 * 并置 [ActiveConfig.usingFallback] 为 true，同时携带遇到的错误（Requirement 4.3/4.4/4.7-4.9、6.1/6.2）。
 *
 * @property loader 提供编写期原始文本的加载器。
 * @property activeLayoutId 活动布局 id（本轮固定 "en"）。
 * @property lastValid 上一份成功生效的配置（若有），用于优先于默认配置的回退。
 */
class KeyboardConfigProvider(
    private val loader: ConfigLoader,
    private val activeLayoutId: String = "en",
    private val lastValid: KeyboardConfig? = null
) {

    /**
     * 执行一轮配置加载编排，返回当前生效配置。
     *
     * 编排顺序：
     * 1. [ConfigLoader.loadAuthoringSources] 读取 active layout 及其 theme/template 原始文本；失败回退。
     * 2. [KeyboardConfigParser.parseAuthoring] 反序列化为 authoring 对象；失败回退。
     * 3. [KeyboardConfigResolver.resolve] 展开为扁平配置（内部已执行校验，R3.14-15）；失败回退。
     * 4. 作为防御，额外再跑一次 [KeyboardConfigValidator.validate]；非空即回退（R6.1）。
     * 5. 全部通过则返回 `usingFallback = false`、`errors = emptyList()` 的生效配置。
     *
     * 回退时优先采用 [lastValid]，否则采用 [DefaultConfig.config]，并置 `usingFallback = true`。
     */
    suspend fun load(): ActiveConfig {
        // 步骤 1：加载编写期原始文本。
        val loadResult = loader.loadAuthoringSources(activeLayoutId)
        val sources = when (loadResult) {
            is LoadResult.Success -> loadResult.sources
            is LoadResult.Failure -> return fallback(listOf(loadResult.error))
        }

        // 步骤 2：反序列化为 authoring 对象。
        val parseResult = KeyboardConfigParser.parseAuthoring(sources)
        val parsed = when (parseResult) {
            is AuthoringParseResult.Success -> parseResult
            is AuthoringParseResult.Failure -> return fallback(listOf(parseResult.error))
        }

        // 步骤 3：展开为扁平配置（resolve 内部已执行校验）。
        val resolveResult = KeyboardConfigResolver.resolve(parsed.layout, parsed.theme, parsed.templates)
        val resolved = when (resolveResult) {
            is ResolveResult.Success -> resolveResult.config
            is ResolveResult.Failure -> return fallback(listOf(resolveResult.error))
        }

        // 步骤 4：防御性再校验，任何残留问题都触发回退。
        val validationErrors = KeyboardConfigValidator.validate(resolved)
        if (validationErrors.isNotEmpty()) {
            return fallback(validationErrors)
        }

        // 步骤 5：全部通过，配置正常生效。
        return ActiveConfig(config = resolved, usingFallback = false, errors = emptyList())
    }

    /**
     * 构造回退结果：优先采用 [lastValid]，否则采用 [DefaultConfig.config]，
     * 置 `usingFallback = true` 并携带本轮遇到的 [errors]。
     */
    private fun fallback(errors: List<ConfigError>): ActiveConfig =
        ActiveConfig(
            config = lastValid ?: DefaultConfig.config,
            usingFallback = true,
            errors = errors
        )
}
