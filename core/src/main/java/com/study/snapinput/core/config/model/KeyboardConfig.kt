package com.study.snapinput.core.config.model

import kotlinx.serialization.Serializable

/**
 * 扁平运行时键盘配置（Resolver 的展开产物，渲染/缩放/布局/校验的唯一消费模型）。
 *
 * 约定：
 * - 所有尺寸类字段均为**无单位比例**（`Float`），以 360×800 参考分辨率标定，运行时按设备像素换算。
 * - 颜色统一为 `#AARRGGBB` 字符串，绘制时再解析为颜色。
 * - 唯一例外是 [cornerRadiusDp]，它是固定 dp，不随分辨率缩放。
 *
 * 注释中标注的取值区间为校验器（KeyboardConfigValidator）所约束的合法范围，本数据类本身不做校验。
 */
@Serializable
data class KeyboardConfig(
    /** 键盘区高度比例，占屏幕高度 H；合法范围 (0,1]。 */
    val keyboardRegionHeightRatio: Float,
    /** 两侧外边距比例，占键盘宽度 W；合法范围 [0,1]。 */
    val sideMarginRatio: Float,
    /** 水平间距比例，占键盘宽度 W；合法范围 [0,1]。 */
    val horizontalGapRatio: Float,
    /** 顶部外边距比例，占键盘区高度；合法范围 [0,1]。 */
    val topMarginRatio: Float,
    /** 底部外边距比例，占键盘区高度；合法范围 [0,1]。 */
    val bottomMarginRatio: Float,
    /** 垂直间距比例，占键盘区高度；合法范围 [0,1]。 */
    val verticalGapRatio: Float,
    /** 正常键高比例，占键盘区高度，键盘级统一；合法范围 (0,1]。 */
    val normalKeyHeightRatio: Float,
    /** 圆角半径，固定 dp（不缩放）；合法范围 0..256。 */
    val cornerRadiusDp: Float,
    /** 行列表；行数合法范围 1..16。 */
    val rows: List<RowConfig>
)
