package com.study.snapinput.core.config

import com.study.snapinput.core.config.authoring.InlineKey
import com.study.snapinput.core.config.authoring.KeySpec
import com.study.snapinput.core.config.authoring.KeySpecOrRef
import com.study.snapinput.core.config.authoring.KeyTemplateRef
import com.study.snapinput.core.config.authoring.LayoutFile
import com.study.snapinput.core.config.authoring.RowSpec
import com.study.snapinput.core.config.authoring.TemplateFile
import com.study.snapinput.core.config.authoring.Theme
import com.study.snapinput.core.config.model.KeyboardConfig
import com.study.snapinput.core.config.model.KeyConfig
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * 扁平运行时配置（[KeyboardConfig]）的解析结果。
 *
 * - [Success]：解析成功，携带字段已补全的运行时配置（含推导出的按下态背景色）。
 * - [Failure]：解析失败，携带带字段信息的 [ConfigError]，且不产出任何配置。
 */
sealed interface ParseResult {
    /** 解析成功。 */
    data class Success(val config: KeyboardConfig) : ParseResult
    /** 解析失败，原因见 [error]。 */
    data class Failure(val error: ConfigError) : ParseResult
}

/**
 * 编写期配置（authoring）反序列化结果。
 *
 * - [Success]：layout / theme / 全部 template 文本均成功反序列化为对应的 authoring 对象。
 * - [Failure]：任一文件语法错误、缺必填字段、类型不符或颜色非法，携带带「文件 + 字段」信息的
 *   [ConfigError]，且不产出任何 authoring 对象与运行时配置（R5.11）。
 */
sealed interface AuthoringParseResult {
    /** 反序列化成功，携带 layout、theme 与按 layout.templates 顺序的 template 列表。 */
    data class Success(
        val layout: LayoutFile,
        val theme: Theme,
        val templates: List<TemplateFile>
    ) : AuthoringParseResult

    /** 反序列化失败，原因见 [error]。 */
    data class Failure(val error: ConfigError) : AuthoringParseResult
}

/**
 * 扁平运行时 [KeyboardConfig] 的解析器与序列化器（基于 kotlinx.serialization Json）。
 *
 * 职责（仅限本任务范围）：
 * - [parse]：将运行时配置 JSON 文本反序列化为 [KeyboardConfig]；语法错误、缺必填字段、
 *   类型不符、颜色非法分别返回带字段信息的 [ConfigError]，且不产出配置（R5.4-R5.7）。
 *   省略 `subLabel` 时产出不带子标签的 [KeyConfig]（R5.2）；省略 `pressedBackgroundColor`
 *   时按 RR/GG/BB 各 ×0.8 向下取整、AA 不变推导（R5.3 / R1.14）。
 * - [serialize]：将 [KeyboardConfig] 序列化为符合 Schema 的 JSON 文本（R5.8）。
 *
 * 注意：数值范围、填充/分区等值约束由 KeyboardConfigValidator 负责，本解析器只覆盖
 * 语法/结构/颜色格式层面的错误。
 */
object KeyboardConfigParser {

    /** `#AARRGGBB` 颜色格式：'#' 后恰好 8 位十六进制（大小写不敏感）。 */
    private val COLOR_REGEX = Regex("^#[0-9A-Fa-f]{8}$")

    private val json = Json {
        encodeDefaults = false
        explicitNulls = false
        ignoreUnknownKeys = false
        prettyPrint = false
    }

    /**
     * 解析运行时配置 JSON 文本。
     *
     * @param json 运行时配置 JSON 文本。
     * @return [ParseResult.Success]（含补全后的配置）或 [ParseResult.Failure]（带字段信息）。
     */
    fun parse(json: String): ParseResult {
        // 第一步：仅做语法检查，将 JSON 文本解析为中间元素树。
        val element: JsonElement = try {
            this.json.parseToJsonElement(json)
        } catch (e: SerializationException) {
            return ParseResult.Failure(
                ConfigError(field = "<root>", reason = Reason.SYNTAX, offendingValue = e.message)
            )
        }

        // 第二步：将元素树绑定到模型；区分缺字段与类型不符。
        val raw: KeyboardConfig = try {
            this.json.decodeFromJsonElement(element)
        } catch (e: MissingFieldException) {
            return ParseResult.Failure(
                ConfigError(
                    field = e.missingFields.joinToString(", "),
                    reason = Reason.MISSING_FIELD,
                    offendingValue = e.message
                )
            )
        } catch (e: SerializationException) {
            return ParseResult.Failure(
                ConfigError(field = "<unknown>", reason = Reason.TYPE_MISMATCH, offendingValue = e.message)
            )
        }

        // 第三步：校验所有颜色字段格式，定位到具体字段。
        validateColors(raw)?.let { return ParseResult.Failure(it) }

        // 第四步：为省略按下态背景色的按键推导默认值。
        return ParseResult.Success(fillDerivedPressedColors(raw))
    }

    /**
     * 将 [KeyboardConfig] 序列化为 JSON 文本。
     *
     * @param config 待序列化的运行时配置。
     * @return 符合 Schema 的 JSON 文本。
     */
    fun serialize(config: KeyboardConfig): String =
        json.encodeToString(KeyboardConfig.serializer(), config)

    /**
     * 将一组编写期源文本（layout / theme / 各 template）反序列化为 authoring 对象。
     *
     * 处理流程：
     * 1. 逐文件先做语法检查（[SerializationException] → [Reason.SYNTAX]），再绑定模型
     *    （[MissingFieldException] → [Reason.MISSING_FIELD]；其余 [SerializationException] 及自定义
     *    序列化器抛出的 [IllegalArgumentException] / [IllegalStateException] → [Reason.TYPE_MISMATCH]）。
     * 2. 全部反序列化成功后，扫描 authoring 中的颜色字段格式（非 `#AARRGGBB` → [Reason.INVALID_COLOR]）。
     *
     * 任一步失败均返回 [AuthoringParseResult.Failure]，其 [ConfigError.field] 同时标识出错文件
     * （如 `keyboard/themes/{id}.json`）与字段，且不产出任何 authoring 对象（R5.10 / R5.11）。
     *
     * @param sources 一次加载得到的全部编写期原始文本。
     */
    fun parseAuthoring(sources: AuthoringSources): AuthoringParseResult {
        val layoutLabel = "keyboard/layouts/${sources.layoutId}.json"
        val themeLabel = "keyboard/themes/${sources.themeId}.json"
        return try {
            // 第一步：逐文件反序列化（顺序：layout → theme → templates）。
            val layout = decodeFile(layoutLabel, sources.layoutJson, LayoutFile.serializer())
            val theme = decodeFile(themeLabel, sources.themeJson, Theme.serializer())
            val namedTemplates = sources.templates.map { (name, text) ->
                name to decodeFile("keyboard/templates/$name.json", text, TemplateFile.serializer())
            }

            // 第二步：扫描全部 authoring 颜色字段格式。
            validateAuthoringColors(themeLabel, theme, layoutLabel, layout, namedTemplates)
                ?.let { return AuthoringParseResult.Failure(it) }

            AuthoringParseResult.Success(
                layout = layout,
                theme = theme,
                templates = namedTemplates.map { it.second }
            )
        } catch (e: AuthoringParseException) {
            AuthoringParseResult.Failure(e.error)
        }
    }

    /** 反序列化过程中携带 [ConfigError] 向上抛出的内部异常，统一在 [parseAuthoring] 处转为 Failure。 */
    private class AuthoringParseException(val error: ConfigError) : Exception()

    /**
     * 反序列化单个 authoring 文件文本；任一类错误均抛出 [AuthoringParseException]，
     * 其 [ConfigError.field] 以 [fileLabel] 标识出错文件（必要时附加字段）。
     */
    private fun <T> decodeFile(
        fileLabel: String,
        text: String,
        deserializer: DeserializationStrategy<T>
    ): T {
        // 先仅做语法检查，将文本解析为中间元素树。
        val element: JsonElement = try {
            json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            throw AuthoringParseException(
                ConfigError(field = fileLabel, reason = Reason.SYNTAX, offendingValue = e.message)
            )
        }

        // 再将元素树绑定到模型；区分缺字段与类型不符。
        return try {
            json.decodeFromJsonElement(deserializer, element)
        } catch (e: MissingFieldException) {
            throw AuthoringParseException(
                ConfigError(
                    field = "$fileLabel#${e.missingFields.joinToString(", ")}",
                    reason = Reason.MISSING_FIELD,
                    offendingValue = e.message
                )
            )
        } catch (e: SerializationException) {
            throw AuthoringParseException(
                ConfigError(field = fileLabel, reason = Reason.TYPE_MISMATCH, offendingValue = e.message)
            )
        } catch (e: IllegalArgumentException) {
            // 自定义序列化器（如 KeySpecOrRef / AuthoringWidth）通过 require 抛出的结构性错误。
            throw AuthoringParseException(
                ConfigError(field = fileLabel, reason = Reason.TYPE_MISMATCH, offendingValue = e.message)
            )
        } catch (e: IllegalStateException) {
            // 自定义序列化器对无法识别形态抛出的结构性错误。
            throw AuthoringParseException(
                ConfigError(field = fileLabel, reason = Reason.TYPE_MISMATCH, offendingValue = e.message)
            )
        }
    }

    /**
     * 扫描 theme / templates / layout 中的全部 authoring 颜色字段；返回首个非法颜色对应的
     * [ConfigError]（[Reason.INVALID_COLOR]，field 标识文件与字段），全部合法返回 null。
     */
    private fun validateAuthoringColors(
        themeLabel: String,
        theme: Theme,
        layoutLabel: String,
        layout: LayoutFile,
        namedTemplates: List<Pair<String, TemplateFile>>
    ): ConfigError? {
        // theme.keyDefaults 中各 Key_Class 的默认颜色
        theme.keyDefaults.forEach { (cls, defaults) ->
            val prefix = "$themeLabel#keyDefaults.$cls"
            defaults.normalBackgroundColor
                ?.let { colorError("$prefix.normalBackgroundColor", it)?.let { e -> return e } }
            defaults.pressedBackgroundColor
                ?.let { colorError("$prefix.pressedBackgroundColor", it)?.let { e -> return e } }
            defaults.mainText?.color
                ?.let { colorError("$prefix.mainText.color", it)?.let { e -> return e } }
            defaults.subLabel?.color
                ?.let { colorError("$prefix.subLabel.color", it)?.let { e -> return e } }
        }

        // 各 template 文件中的按键模板与行模板
        namedTemplates.forEach { (name, template) ->
            val label = "keyboard/templates/$name.json"
            template.keyTemplates.forEach { (keyName, spec) ->
                scanKeySpecColors("$label#keyTemplates.$keyName", spec)?.let { return it }
            }
            template.rowTemplates.forEach { (rowName, row) ->
                scanRowSpecColors("$label#rowTemplates.$rowName", row)?.let { return it }
            }
        }

        // layout 内联行规格
        layout.rows.forEachIndexed { index, row ->
            if (row is RowSpec) {
                scanRowSpecColors("$layoutLabel#rows[$index]", row)?.let { return it }
            }
        }
        return null
    }

    /** 扫描单个 [KeySpec] 的颜色字段。 */
    private fun scanKeySpecColors(prefix: String, spec: KeySpec): ConfigError? {
        spec.normalBackgroundColor
            ?.let { colorError("$prefix.normalBackgroundColor", it)?.let { e -> return e } }
        spec.pressedBackgroundColor
            ?.let { colorError("$prefix.pressedBackgroundColor", it)?.let { e -> return e } }
        spec.mainText?.color?.let { colorError("$prefix.mainText.color", it)?.let { e -> return e } }
        spec.subLabel?.color?.let { colorError("$prefix.subLabel.color", it)?.let { e -> return e } }
        return null
    }

    /** 扫描 [KeySpecOrRef]（内联规格或带覆盖的模板引用）的颜色字段。 */
    private fun scanKeyOrRefColors(prefix: String, entry: KeySpecOrRef): ConfigError? =
        when (entry) {
            is InlineKey -> scanKeySpecColors(prefix, entry.spec)
            // 仅 per-site 覆盖部分含颜色字段，模板自身在 keyTemplates 中已扫描
            is KeyTemplateRef -> entry.overrides?.let { scanKeySpecColors("$prefix(\$${entry.name})", it) }
        }

    /** 扫描 [RowSpec]（显式键行或字母行简写）中所有按键条目的颜色字段。 */
    private fun scanRowSpecColors(prefix: String, row: RowSpec): ConfigError? {
        when (row) {
            is RowSpec.KeysRow ->
                row.keys.forEachIndexed { i, key ->
                    scanKeyOrRefColors("$prefix.keys[$i]", key)?.let { return it }
                }
            is RowSpec.LettersRow -> {
                row.lead?.let { scanKeyOrRefColors("$prefix.lead", it)?.let { e -> return e } }
                row.trail?.let { scanKeyOrRefColors("$prefix.trail", it)?.let { e -> return e } }
            }
        }
        return null
    }

    /**
     * 遍历配置中的全部颜色字段，返回首个非法颜色对应的 [ConfigError]；全部合法返回 null。
     */
    private fun validateColors(config: KeyboardConfig): ConfigError? {
        config.rows.forEachIndexed { r, row ->
            row.keys.forEachIndexed { k, key ->
                val prefix = "rows[$r].keys[$k]"
                colorError("$prefix.normalBackgroundColor", key.normalBackgroundColor)?.let { return it }
                key.pressedBackgroundColor?.let { c ->
                    colorError("$prefix.pressedBackgroundColor", c)?.let { return it }
                }
                colorError("$prefix.mainText.color", key.mainText.color)?.let { return it }
                key.subLabel?.let { sub ->
                    colorError("$prefix.subLabel.color", sub.color)?.let { return it }
                }
            }
        }
        return null
    }

    /** 校验单个颜色值；非法返回 [ConfigError]，合法返回 null。 */
    private fun colorError(field: String, value: String): ConfigError? =
        if (COLOR_REGEX.matches(value)) {
            null
        } else {
            ConfigError(field = field, reason = Reason.INVALID_COLOR, offendingValue = value)
        }

    /**
     * 为每个省略 `pressedBackgroundColor` 的按键推导默认按下态背景色。
     * 推导规则：RR/GG/BB 三个分量各自 ×0.8 向下取整，AA 分量保持不变。
     */
    private fun fillDerivedPressedColors(config: KeyboardConfig): KeyboardConfig =
        config.copy(
            rows = config.rows.map { row ->
                row.copy(
                    keys = row.keys.map { key ->
                        if (key.pressedBackgroundColor == null) {
                            key.copy(pressedBackgroundColor = derivePressedColor(key.normalBackgroundColor))
                        } else {
                            key
                        }
                    }
                )
            }
        )

    /**
     * 由正常背景色推导按下态背景色：RR/GG/BB 各 ×0.8 向下取整、AA 不变。
     *
     * @param normalArgb 形如 `#AARRGGBB` 的合法颜色字符串（调用前已校验格式）。
     */
    private fun derivePressedColor(normalArgb: String): String {
        val aa = normalArgb.substring(1, 3) // AA 原样保留
        val rr = scaleComponent(normalArgb.substring(3, 5))
        val gg = scaleComponent(normalArgb.substring(5, 7))
        val bb = scaleComponent(normalArgb.substring(7, 9))
        return "#$aa$rr$gg$bb"
    }

    /** 将两位十六进制分量 ×0.8 向下取整后，重新格式化为两位大写十六进制。 */
    private fun scaleComponent(hex: String): String {
        val scaled = Math.floor(hex.toInt(16) * 0.8).toInt()
        return scaled.toString(16).uppercase().padStart(2, '0')
    }
}
