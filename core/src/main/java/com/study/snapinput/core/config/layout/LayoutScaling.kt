package com.study.snapinput.core.config.layout

import com.study.snapinput.core.config.model.KeyboardConfig

/**
 * 纯像素缩放工具：把无单位比例换算为设备像素值（见 Requirement 7）。
 *
 * 缩放规则：
 * - 水平类比例 × 键盘宽度 W（= 屏幕宽度）。
 * - 垂直类比例 × 键盘区高度（= H × keyboardRegionHeightRatio）。
 * - 字号比例 × 正常键高像素。
 * - 圆角为固定 dp，不随分辨率缩放（仅在渲染期按密度转像素，故此处保持 dp）。
 *
 * 这些函数与 [computeKeyRects] 共享同一套换算约定，确保渲染与布局数学一致。
 */
object LayoutScaling {

    /** 键盘区高度像素 = keyboardRegionHeightRatio × 屏幕高度 H。 */
    fun keyboardRegionHeightPx(config: KeyboardConfig, screenHeightPx: Float): Float =
        config.keyboardRegionHeightRatio * screenHeightPx

    /** 左右侧边外边距像素 = Side_Margin_Ratio × W。 */
    fun sideMarginPx(config: KeyboardConfig, keyboardWidthPx: Float): Float =
        config.sideMarginRatio * keyboardWidthPx

    /** 水平间距像素 = Horizontal_Gap_Ratio × W。 */
    fun horizontalGapPx(config: KeyboardConfig, keyboardWidthPx: Float): Float =
        config.horizontalGapRatio * keyboardWidthPx

    /** 单个按键宽度像素 = Key_Width_Ratio × W。 */
    fun keyWidthPx(widthRatio: Float, keyboardWidthPx: Float): Float =
        widthRatio * keyboardWidthPx

    /** 上外边距像素 = Top_Margin_Ratio × 键盘区高度。 */
    fun topMarginPx(config: KeyboardConfig, keyboardRegionHeightPx: Float): Float =
        config.topMarginRatio * keyboardRegionHeightPx

    /** 下外边距像素 = Bottom_Margin_Ratio × 键盘区高度。 */
    fun bottomMarginPx(config: KeyboardConfig, keyboardRegionHeightPx: Float): Float =
        config.bottomMarginRatio * keyboardRegionHeightPx

    /** 行垂直间距像素 = Vertical_Gap_Ratio × 键盘区高度。 */
    fun verticalGapPx(config: KeyboardConfig, keyboardRegionHeightPx: Float): Float =
        config.verticalGapRatio * keyboardRegionHeightPx

    /** 正常键高像素 = Normal_Key_Height_Ratio × 键盘区高度，键盘级统一。 */
    fun keyHeightPx(config: KeyboardConfig, keyboardRegionHeightPx: Float): Float =
        config.normalKeyHeightRatio * keyboardRegionHeightPx

    /** 文本字号像素 = sizeRatio × 正常键高像素（主文本与子标签通用，不使用 sp、不跟随系统字号缩放）。 */
    fun textSizePx(sizeRatio: Float, keyHeightPx: Float): Float =
        sizeRatio * keyHeightPx
}
