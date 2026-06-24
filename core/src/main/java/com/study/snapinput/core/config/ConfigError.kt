package com.study.snapinput.core.config

/**
 * 结构化配置错误：能指明出错字段与原因，便于日志与回退提示。
 *
 * @property field 出错字段或位置（如 `rows[2].keys[0].widthRatio`、`theme`、文件名等）。
 * @property reason 错误原因，见 [Reason]。
 * @property offendingValue 触发错误的原始值（可选），用于诊断。
 */
data class ConfigError(
    val field: String,
    val reason: Reason,
    val offendingValue: String? = null
)

/**
 * 配置错误原因。
 *
 * 前段为扁平运行时配置的解析/校验原因；后段为 authoring / resolve 展开新增原因。
 * 错误归属：缺失/不可读文件由 Loader 报 [MISSING_REFERENCED_FILE]；反序列化语法/类型/缺字段/非法颜色由 Parser 报；
 * 未知引用、重复模板、简写长度不符、未知类由 Resolver 报；行填充剩余 ≤ 0 由 Resolver 报 [ROW_OVERFLOW]。
 */
enum class Reason {
    /** JSON 语法错误。 */
    SYNTAX,
    /** 缺少必填字段。 */
    MISSING_FIELD,
    /** 字段类型不符。 */
    TYPE_MISMATCH,
    /** 数值超出合法范围。 */
    OUT_OF_RANGE,
    /** 颜色格式非法（非 `#AARRGGBB`）。 */
    INVALID_COLOR,
    /** 行水平占用合计 > 1.0。 */
    ROW_OVERFLOW,
    /** 垂直分区合计 ≠ 1.0（容差 ±0.001）。 */
    VERTICAL_PARTITION,

    // —— authoring / resolve 新增 ——

    /** `$ref` 指向不存在的 key/row 模板。 */
    UNKNOWN_REF,
    /** 跨 template 文件存在重名模板。 */
    DUPLICATE_TEMPLATE,
    /** subLabels 数量与 letters 字符数不一致。 */
    LETTERS_SUBLABELS_MISMATCH,
    /** 引用了 theme.keyDefaults 未定义的 Key_Class。 */
    UNKNOWN_KEY_CLASS,
    /** layout 引用的 theme/template 文件缺失。 */
    MISSING_REFERENCED_FILE
}
