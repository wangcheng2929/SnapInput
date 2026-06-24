package com.study.snapinput.core.config.layout

/**
 * 命中测试纯函数：将键盘区坐标系中的触摸点映射到对应按键矩形。
 *
 * 与渲染共用同一组 [KeyRect]（[computeKeyRects] 的输出），保证“绘制原点 = 命中区域”的单一真相源。
 * 矩形采用左闭右开 / 上闭下开区间 `[x, x+width) × [y, y+height)`：
 * - 左/上边界算命中，右/下边界归属相邻按键或间距，避免相邻矩形重复命中。
 *
 * 落在侧边外边距、水平间距、行垂直间距或上下外边距（即不被任何矩形覆盖）时返回 `null`
 * （见 Requirement 12.4）。理论上矩形互不重叠；若出现重叠（不应发生），返回首个匹配项。
 *
 * @param rects 当前键盘的全部按键矩形。
 * @param px 触摸点 X（键盘区左上角为原点，像素）。
 * @param py 触摸点 Y（键盘区左上角为原点，像素）。
 * @return 命中的 [KeyRect]；落在间距/外边距时为 `null`。
 */
fun hitTest(rects: List<KeyRect>, px: Float, py: Float): KeyRect? {
    for (rect in rects) {
        val withinX = px >= rect.x && px < rect.x + rect.width
        val withinY = py >= rect.y && py < rect.y + rect.height
        if (withinX && withinY) {
            return rect
        }
    }
    return null
}
