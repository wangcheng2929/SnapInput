package com.study.snapinput.core.config

import com.study.snapinput.core.config.model.KeyConfig
import com.study.snapinput.core.config.model.KeyboardConfig
import com.study.snapinput.core.config.model.RowConfig
import com.study.snapinput.core.config.model.SubLabel
import com.study.snapinput.core.config.model.TextStyleConfig

/**
 * 内置默认配置（Default_Config）。
 *
 * 设计要点（见 Requirement 6 与设计文档）：
 * - 默认配置以 **authoring 格式**编写，随包打包于 app 资产：
 *   [THEME_ASSET_PATH] / [TEMPLATES_ASSET_PATH] / [LAYOUT_ASSET_PATH]。
 * - 同时**预计算并内嵌**其 Resolver 展开结果——扁平 [KeyboardConfig] 常量 [config]。
 *   回退路径（加载/反序列化/展开/校验任一失败）直接采用 [config]，**不再**执行 resolve/parse，
 *   从而保证默认配置“始终有效、永不失败”（Requirement 6.13）。
 * - 内嵌的 [config] 逐字段等于 Requirement 6 参考值表所定义的默认布局：
 *   4 行 QWERTY、各行 Sub_Label、Special_Key 集合 {Shift, Del, 123, Space, 中/英, Enter}、
 *   比例参考值（360×800 标定）、行水平占用与垂直分区合计。
 *
 * 说明：authoring 源中 `$space` 模板的主文本声明为空串（`""`），但扁平运行时模型要求
 * 主文本内容长度为 1..32（KeyboardConfigValidator）。因此内嵌默认中 Space 键主文本取单个空格
 * `" "`（长度 1），以使常量通过校验。
 */
object DefaultConfig {

    /** authoring 源——主题文件（themeId "light"）在 app 资产中的路径。 */
    const val THEME_ASSET_PATH: String = "keyboard/themes/light.json"

    /** authoring 源——模板文件（名为 "common"）在 app 资产中的路径。 */
    const val TEMPLATES_ASSET_PATH: String = "keyboard/templates/common.json"

    /** authoring 源——英文布局文件（layoutId "en"）在 app 资产中的路径。 */
    const val LAYOUT_ASSET_PATH: String = "keyboard/layouts/en.json"

    /** 本轮固定的活动布局 id（Active_Layout）。 */
    const val ACTIVE_LAYOUT_ID: String = "en"

    // —— 键盘级比例参考值（360×800 标定，见 Requirement 6.14）——
    private const val SIDE_MARGIN_RATIO: Float = 7f / 360f
    private const val HORIZONTAL_GAP_RATIO: Float = 4f / 360f
    private const val TOP_MARGIN_RATIO: Float = 4f / 216f
    private const val BOTTOM_MARGIN_RATIO: Float = 4f / 216f
    private const val VERTICAL_GAP_RATIO: Float = 8f / 216f
    private const val NORMAL_KEY_HEIGHT_RATIO: Float = 46f / 216f

    // —— 按键宽度参考值（占键盘宽度 W）——
    private const val LETTER_WIDTH: Float = 31f / 360f
    private const val SHIFT_WIDTH: Float = 48.5f / 360f
    private const val DEL_WIDTH: Float = 48.5f / 360f
    private const val KEY123_WIDTH: Float = 46f / 360f
    private const val SPACE_WIDTH: Float = 161f / 360f
    private const val LANG_WIDTH: Float = 46f / 360f
    private const val ENTER_WIDTH: Float = 46f / 360f

    // —— 颜色（来自 theme light.json 的 Key_Defaults）——
    private const val LETTER_BG: String = "#FFFFFFFF"
    private const val SPECIAL_BG: String = "#FFADB3BD"
    private const val MAIN_TEXT_COLOR: String = "#FF000000"
    private const val SUB_LABEL_COLOR: String = "#80000000"

    // —— 文本尺寸比例（占正常键高）——
    private const val LETTER_MAIN_SIZE: Float = 0.45f
    private const val SPECIAL_MAIN_SIZE: Float = 0.40f
    private const val SUB_LABEL_SIZE: Float = 0.22f

    /**
     * 内嵌的扁平默认 [KeyboardConfig]（Default_Config 的预计算展开结果）。
     * 回退路径直接采用此常量。
     */
    val config: KeyboardConfig = KeyboardConfig(
        keyboardRegionHeightRatio = 0.27f,
        sideMarginRatio = SIDE_MARGIN_RATIO,
        horizontalGapRatio = HORIZONTAL_GAP_RATIO,
        topMarginRatio = TOP_MARGIN_RATIO,
        bottomMarginRatio = BOTTOM_MARGIN_RATIO,
        verticalGapRatio = VERTICAL_GAP_RATIO,
        normalKeyHeightRatio = NORMAL_KEY_HEIGHT_RATIO,
        cornerRadiusDp = 8f,
        rows = listOf(
            // 第 1 行：Q W E R T Y U I O P，子标签 1..0
            RowConfig(
                buildLetterRow(
                    "QWERTYUIOP",
                    listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
                )
            ),
            // 第 2 行：A S D F G H J K L，子标签 - / : ; ( ) ~ “ ”
            RowConfig(
                buildLetterRow(
                    "ASDFGHJKL",
                    listOf("-", "/", ":", ";", "(", ")", "~", "\u201C", "\u201D")
                )
            ),
            // 第 3 行：Shift、Z X C V B N M、Del，字母子标签 @ . # 、 ? ! …
            RowConfig(
                buildList {
                    add(specialKey(action = "Shift", main = "\u21E7", width = SHIFT_WIDTH))
                    addAll(
                        buildLetterRow(
                            "ZXCVBNM",
                            listOf("@", ".", "#", "\u3001", "?", "!", "\u2026")
                        )
                    )
                    add(specialKey(action = "Del", main = "\u232B", width = DEL_WIDTH))
                }
            ),
            // 第 4 行：123、逗号键、Space、中/英、Enter（换行）
            RowConfig(
                listOf(
                    specialKey(action = "123", main = "123", width = KEY123_WIDTH),
                    commaKey(),
                    specialKey(action = " ", main = " ", width = SPACE_WIDTH),
                    specialKey(action = "中/英", main = "中/英", width = LANG_WIDTH),
                    specialKey(action = "Enter", main = "换行", width = ENTER_WIDTH)
                )
            )
        )
    )

    /** 由大写字母串与对齐的子标签串生成一行 `letter` 类按键。 */
    private fun buildLetterRow(letters: String, subLabels: List<String>): List<KeyConfig> {
        require(letters.length == subLabels.size) {
            "letters 与 subLabels 长度必须一致：${letters.length} vs ${subLabels.size}"
        }
        return letters.mapIndexed { index, ch -> letterKey(ch.toString(), subLabels[index]) }
    }

    /** 字母键：主文本为字母本身，Action_Value 为其小写形式，带子标签。 */
    private fun letterKey(main: String, sub: String): KeyConfig = KeyConfig(
        action = main.lowercase(),
        widthRatio = LETTER_WIDTH,
        normalBackgroundColor = LETTER_BG,
        mainText = TextStyleConfig(content = main, color = MAIN_TEXT_COLOR, sizeRatio = LETTER_MAIN_SIZE),
        subLabel = SubLabel(content = sub, color = SUB_LABEL_COLOR, sizeRatio = SUB_LABEL_SIZE)
    )

    /** 逗号键：keyClass 为 `letter`（白底字母样式），主文本 "，"、子标签 "。"。 */
    private fun commaKey(): KeyConfig = KeyConfig(
        action = "，",
        widthRatio = LETTER_WIDTH,
        normalBackgroundColor = LETTER_BG,
        mainText = TextStyleConfig(content = "，", color = MAIN_TEXT_COLOR, sizeRatio = LETTER_MAIN_SIZE),
        subLabel = SubLabel(content = "。", color = SUB_LABEL_COLOR, sizeRatio = SUB_LABEL_SIZE)
    )

    /** 特殊键：`special` 类（灰底），无子标签。 */
    private fun specialKey(action: String, main: String, width: Float): KeyConfig = KeyConfig(
        action = action,
        widthRatio = width,
        normalBackgroundColor = SPECIAL_BG,
        mainText = TextStyleConfig(content = main, color = MAIN_TEXT_COLOR, sizeRatio = SPECIAL_MAIN_SIZE)
    )
}
