package com.study.snapinput.core.config.authoring

import kotlinx.serialization.Serializable

/**
 * 编写期主题（Theme）：`keyboard/themes/{themeId}.json` 的反序列化模型。
 *
 * 编写期模型与扁平运行时模型完全分离：authoring 字段大多可选，缺省由 Resolver 按优先级补全。
 * 颜色统一为 `#AARRGGBB` 字符串，尺寸类字段为无单位比例。
 */
@Serializable
data class Theme(
    /** 主题标识，应与文件名一致。 */
    val id: String,
    /** 键盘区高度比例，占屏幕高度 H。 */
    val keyboardRegionHeightRatio: Float,
    /** 两侧外边距比例，占键盘宽度 W。 */
    val sideMarginRatio: Float,
    /** 水平间距比例，占键盘宽度 W。 */
    val horizontalGapRatio: Float,
    /** 顶部外边距比例，占键盘区高度。 */
    val topMarginRatio: Float,
    /** 底部外边距比例，占键盘区高度。 */
    val bottomMarginRatio: Float,
    /** 垂直间距比例，占键盘区高度。 */
    val verticalGapRatio: Float,
    /** 正常键高比例，占键盘区高度。 */
    val normalKeyHeightRatio: Float,
    /** 圆角半径，固定 dp（不缩放）。 */
    val cornerRadiusDp: Float,
    /** 各 Key_Class 的默认值；key 为 Key_Class 名（至少含 "letter" / "special"）。 */
    val keyDefaults: Map<String, KeyDefaults>
)

/**
 * 某个 Key_Class 的编写期默认值。所有字段可选，由 Resolver 与模板/内联值按优先级合并。
 */
@Serializable
data class KeyDefaults(
    /** 默认固定宽度比例（(0,1]）。 */
    val widthRatio: Float? = null,
    /** 默认正常态背景色，`#AARRGGBB`。 */
    val normalBackgroundColor: String? = null,
    /** 默认按下态背景色，`#AARRGGBB`。 */
    val pressedBackgroundColor: String? = null,
    /** 主文本默认样式（仅 color / sizeRatio，无 content）。 */
    val mainText: TextStyleDefaults? = null,
    /** 子标签默认样式（仅 color / sizeRatio，无 content）。 */
    val subLabel: TextStyleDefaults? = null
)

/**
 * 文本默认样式：仅含颜色与字号比例，不含文本内容。
 */
@Serializable
data class TextStyleDefaults(
    /** 文本颜色，`#AARRGGBB`。 */
    val color: String? = null,
    /** 字号比例，占正常键高。 */
    val sizeRatio: Float? = null
)
