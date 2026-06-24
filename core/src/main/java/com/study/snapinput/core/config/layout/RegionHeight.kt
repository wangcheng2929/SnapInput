package com.study.snapinput.core.config.layout

/**
 * 区域高度收缩结果：收缩后的 Top_Region 与 Keyboard_Region 像素高度。
 *
 * 当无需收缩时，两值与输入相同。
 */
data class RegionHeights(
    /** 收缩后的 Top_Region 高度（像素）。 */
    val topRegionPx: Float,
    /** 收缩后的 Keyboard_Region 高度（像素）。 */
    val keyboardRegionPx: Float
)

/**
 * 区域高度收缩纯函数（见 Requirement 9.5）。
 *
 * 当 `topRegionPx + keyboardRegionPx` 超过可用显示高度时，两区域按同一系数
 * `availableHeightPx / (topRegionPx + keyboardRegionPx)` 同时收缩，使其和恰好约束为可用高度，
 * 同时保持两者的相对比例不变；否则原样返回。
 *
 * 边界处理：当两区域高度之和小于等于 0 时（不应发生），无法计算有效系数，原样返回，避免除零。
 *
 * @param topRegionPx Top_Region 原始高度（= H × Top_Region_Height_Ratio，像素）。
 * @param keyboardRegionPx Keyboard_Region 原始高度（= H × Keyboard_Region_Height_Ratio，像素）。
 * @param availableHeightPx 可用显示高度（像素）。
 * @return 收缩后的两区域高度；未超限时与输入一致。
 */
fun shrinkRegionHeights(
    topRegionPx: Float,
    keyboardRegionPx: Float,
    availableHeightPx: Float
): RegionHeights {
    val total = topRegionPx + keyboardRegionPx
    // 未超限或总和非正：原样返回（后者避免除零）。
    if (total <= 0f || total <= availableHeightPx) {
        return RegionHeights(topRegionPx, keyboardRegionPx)
    }
    // 超限：两区域按同一系数收缩，使其和约束为可用高度。
    val factor = availableHeightPx / total
    return RegionHeights(
        topRegionPx = topRegionPx * factor,
        keyboardRegionPx = keyboardRegionPx * factor
    )
}
