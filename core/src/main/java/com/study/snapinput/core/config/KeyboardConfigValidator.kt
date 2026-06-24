package com.study.snapinput.core.config

import com.study.snapinput.core.config.model.KeyboardConfig
import com.study.snapinput.core.config.model.KeyConfig
import kotlin.math.abs

/**
 * 扁平运行时 [KeyboardConfig] 的校验器。
 *
 * 校验内容（对应 Requirement 1.18–1.24）：
 * 1. 全部键盘级比例字段范围与 [KeyboardConfig.cornerRadiusDp]（0–256 dp）；
 * 2. 每个 [KeyConfig.widthRatio]、主文本与子标签 `sizeRatio` 范围；
 * 3. 结构长度：行数 1–16、每行按键数 1–32、文本与子标签内容 1–32 字符；
 * 4. 全部颜色字段格式 `#AARRGGBB`（AA/RR/GG/BB 各两位十六进制，大小写不敏感）；
 * 5. 行水平占用合计 ≤ 1.0（`Σ widthRatio + (n−1)×Horizontal_Gap_Ratio + 2×Side_Margin_Ratio`）；
 * 6. 垂直分区合计 = 1.0（容差 ±0.001）。
 *
 * 校验器不抛异常，而是收集全部问题以 [ConfigError] 列表返回；列表为空即表示配置合法。
 */
object KeyboardConfigValidator {

    /** `#AARRGGBB` 颜色格式：恰好 8 位十六进制，大小写不敏感。 */
    private val COLOR_REGEX = Regex("^#[0-9A-Fa-f]{8}$")

    /** 圆角半径合法上界（dp）。 */
    private const val CORNER_RADIUS_MAX = 256f

    /** 文本字号比例合法上界（占正常键高的分数）。 */
    private const val TEXT_SIZE_RATIO_MAX = 5f

    /** 结构长度约束。 */
    private const val ROWS_MIN = 1
    private const val ROWS_MAX = 16
    private const val KEYS_MIN = 1
    private const val KEYS_MAX = 32
    private const val TEXT_LEN_MIN = 1
    private const val TEXT_LEN_MAX = 32

    /** 行水平占用合计的浮点容差，避免恰好填满（合计 = 1.0）的行因浮点误差被误判。 */
    private const val HORIZONTAL_TOLERANCE = 1e-4f

    /** 垂直分区合计与 1.0 的允许偏差（Requirement 1.24）。 */
    private const val VERTICAL_TOLERANCE = 0.001f

    /**
     * 校验 [config]，返回全部错误；返回空列表表示配置合法。
     * 每个错误通过 [ConfigError.field] 指明出错字段或行。
     */
    fun validate(config: KeyboardConfig): List<ConfigError> {
        val errors = mutableListOf<ConfigError>()

        validateKeyboardLevelFields(config, errors)
        validateStructureAndKeys(config, errors)
        validateRowHorizontalOccupancy(config, errors)
        validateVerticalPartition(config, errors)

        return errors
    }

    /** 校验键盘级比例字段范围与圆角半径。 */
    private fun validateKeyboardLevelFields(config: KeyboardConfig, errors: MutableList<ConfigError>) {
        // (0,1] 区间字段
        checkRangeExclusiveZero("keyboardRegionHeightRatio", config.keyboardRegionHeightRatio, 1f, errors)
        checkRangeExclusiveZero("normalKeyHeightRatio", config.normalKeyHeightRatio, 1f, errors)

        // [0,1] 区间字段
        checkRangeInclusive("sideMarginRatio", config.sideMarginRatio, 0f, 1f, errors)
        checkRangeInclusive("horizontalGapRatio", config.horizontalGapRatio, 0f, 1f, errors)
        checkRangeInclusive("topMarginRatio", config.topMarginRatio, 0f, 1f, errors)
        checkRangeInclusive("bottomMarginRatio", config.bottomMarginRatio, 0f, 1f, errors)
        checkRangeInclusive("verticalGapRatio", config.verticalGapRatio, 0f, 1f, errors)

        // Corner_Radius：[0,256] dp
        checkRangeInclusive("cornerRadiusDp", config.cornerRadiusDp, 0f, CORNER_RADIUS_MAX, errors)
    }

    /** 校验结构长度、每个按键字段范围、文本长度与全部颜色格式。 */
    private fun validateStructureAndKeys(config: KeyboardConfig, errors: MutableList<ConfigError>) {
        // 行数 1..16
        if (config.rows.size !in ROWS_MIN..ROWS_MAX) {
            errors += ConfigError("rows", Reason.OUT_OF_RANGE, config.rows.size.toString())
        }

        config.rows.forEachIndexed { rowIndex, row ->
            // 每行按键数 1..32
            if (row.keys.size !in KEYS_MIN..KEYS_MAX) {
                errors += ConfigError("rows[$rowIndex].keys", Reason.OUT_OF_RANGE, row.keys.size.toString())
            }
            row.keys.forEachIndexed { keyIndex, key ->
                validateKey(rowIndex, keyIndex, key, errors)
            }
        }
    }

    /** 校验单个按键的宽度、文本范围/长度与颜色格式。 */
    private fun validateKey(rowIndex: Int, keyIndex: Int, key: KeyConfig, errors: MutableList<ConfigError>) {
        val prefix = "rows[$rowIndex].keys[$keyIndex]"

        // Key_Width_Ratio：(0,1]
        checkRangeExclusiveZero("$prefix.widthRatio", key.widthRatio, 1f, errors)

        // 正常/按下态背景色
        checkColor("$prefix.normalBackgroundColor", key.normalBackgroundColor, errors)
        key.pressedBackgroundColor?.let { checkColor("$prefix.pressedBackgroundColor", it, errors) }

        // 主文本：内容长度 1..32、颜色、Main_Text_Size_Ratio (0,5]
        checkTextLength("$prefix.mainText.content", key.mainText.content, errors)
        checkColor("$prefix.mainText.color", key.mainText.color, errors)
        checkRangeExclusiveZero("$prefix.mainText.sizeRatio", key.mainText.sizeRatio, TEXT_SIZE_RATIO_MAX, errors)

        // 子标签（可选）：内容长度 1..32、颜色、Sub_Label_Size_Ratio (0,5]
        key.subLabel?.let { sub ->
            checkTextLength("$prefix.subLabel.content", sub.content, errors)
            checkColor("$prefix.subLabel.color", sub.color, errors)
            checkRangeExclusiveZero("$prefix.subLabel.sizeRatio", sub.sizeRatio, TEXT_SIZE_RATIO_MAX, errors)
        }
    }

    /**
     * 校验每行水平占用合计 ≤ 1.0：
     * `Σ widthRatio + (按键数 − 1) × Horizontal_Gap_Ratio + 2 × Side_Margin_Ratio`。
     */
    private fun validateRowHorizontalOccupancy(config: KeyboardConfig, errors: MutableList<ConfigError>) {
        config.rows.forEachIndexed { rowIndex, row ->
            if (row.keys.isEmpty()) return@forEachIndexed
            val keyWidthSum = row.keys.fold(0f) { acc, key -> acc + key.widthRatio }
            val gaps = (row.keys.size - 1) * config.horizontalGapRatio
            val margins = 2f * config.sideMarginRatio
            val occupancy = keyWidthSum + gaps + margins
            if (occupancy > 1f + HORIZONTAL_TOLERANCE) {
                errors += ConfigError("rows[$rowIndex]", Reason.ROW_OVERFLOW, occupancy.toString())
            }
        }
    }

    /**
     * 校验垂直分区合计与 1.0 的偏差 ≤ ±0.001：
     * `Top_Margin_Ratio + Bottom_Margin_Ratio + 行数 × Normal_Key_Height_Ratio + (行数 − 1) × Vertical_Gap_Ratio`。
     */
    private fun validateVerticalPartition(config: KeyboardConfig, errors: MutableList<ConfigError>) {
        val rowCount = config.rows.size
        if (rowCount == 0) return
        val partition = config.topMarginRatio +
            config.bottomMarginRatio +
            rowCount * config.normalKeyHeightRatio +
            (rowCount - 1) * config.verticalGapRatio
        if (abs(partition - 1f) > VERTICAL_TOLERANCE) {
            errors += ConfigError("verticalPartition", Reason.VERTICAL_PARTITION, partition.toString())
        }
    }

    // —— 通用校验工具 ——

    /** 校验值落在 (0, max] 区间，否则记录 [Reason.OUT_OF_RANGE]。 */
    private fun checkRangeExclusiveZero(
        field: String,
        value: Float,
        max: Float,
        errors: MutableList<ConfigError>
    ) {
        if (!value.isFinite() || value <= 0f || value > max) {
            errors += ConfigError(field, Reason.OUT_OF_RANGE, value.toString())
        }
    }

    /** 校验值落在 [min, max] 区间，否则记录 [Reason.OUT_OF_RANGE]。 */
    private fun checkRangeInclusive(
        field: String,
        value: Float,
        min: Float,
        max: Float,
        errors: MutableList<ConfigError>
    ) {
        if (!value.isFinite() || value < min || value > max) {
            errors += ConfigError(field, Reason.OUT_OF_RANGE, value.toString())
        }
    }

    /** 校验颜色格式 `#AARRGGBB`，否则记录 [Reason.INVALID_COLOR]。 */
    private fun checkColor(field: String, value: String, errors: MutableList<ConfigError>) {
        if (!COLOR_REGEX.matches(value)) {
            errors += ConfigError(field, Reason.INVALID_COLOR, value)
        }
    }

    /** 校验文本内容长度 1..32 字符，否则记录 [Reason.OUT_OF_RANGE]。 */
    private fun checkTextLength(field: String, value: String, errors: MutableList<ConfigError>) {
        if (value.length !in TEXT_LEN_MIN..TEXT_LEN_MAX) {
            errors += ConfigError(field, Reason.OUT_OF_RANGE, value.length.toString())
        }
    }
}
