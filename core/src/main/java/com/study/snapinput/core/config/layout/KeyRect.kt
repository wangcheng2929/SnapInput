package com.study.snapinput.core.config.layout

import com.study.snapinput.core.config.model.KeyboardConfig

/**
 * 单个按键在键盘区坐标系中的像素矩形（左上角原点 + 宽高）。
 *
 * 绘制原点与命中测试的单一真相源：渲染按此原点落笔，触摸命中测试在同一矩形上进行。
 * 坐标原点为键盘区左上角，单位均为像素（[Float]）。
 */
data class KeyRect(
    /** 所属行下标（从 0 开始），与 [KeyboardConfig.rows] 顺序一致。 */
    val rowIndex: Int,
    /** 行内按键下标（从 0 开始），与该行 keys 顺序一致。 */
    val keyIndex: Int,
    /** 该按键的 Action_Value，便于命中后直接取用。 */
    val action: String,
    /** 左上角 X（像素）。 */
    val x: Float,
    /** 左上角 Y（像素）。 */
    val y: Float,
    /** 宽度（像素）。 */
    val width: Float,
    /** 高度（像素），所有按键一致。 */
    val height: Float
)

/**
 * 由配置与设备像素宽高计算所有按键的像素矩形，同时供渲染（绘制原点）与触摸（命中测试）使用——单一真相源。
 *
 * 缩放约定（见 Requirement 7）：
 * - 水平类比例（[KeyboardConfig.sideMarginRatio]、[KeyboardConfig.horizontalGapRatio]、各键 widthRatio）× [keyboardWidthPx]（= 屏幕宽度 W）。
 * - 垂直类比例（[KeyboardConfig.topMarginRatio]、[KeyboardConfig.verticalGapRatio]、[KeyboardConfig.normalKeyHeightRatio]）× [keyboardRegionHeightPx]（= 键盘区高度 H）。
 *
 * 行垂直坐标：`rowTop(r) = topMargin + r × (keyHeight + vGap)`（所有行等高）。
 *
 * 行水平居中：
 * - `rowContent = Σ keyWidth_i + (n − 1) × hGap`（行内容总宽）
 * - `leftover = (W − 2 × sideMargin) − rowContent`（可用内部宽度减去内容宽）
 * - `rowStartX = sideMargin + leftover / 2`（居中起点；leftover ≈ 0 时该行填满键盘宽度）
 * - `x(k) = rowStartX + Σ_{i<k}(keyWidth_i + hGap)`
 *
 * @param config 已展开、字段完全补全的扁平运行时配置。
 * @param keyboardWidthPx 键盘宽度（= 屏幕宽度 W），像素。
 * @param keyboardRegionHeightPx 键盘区高度（= H × keyboardRegionHeightRatio），像素。
 */
fun computeKeyRects(
    config: KeyboardConfig,
    keyboardWidthPx: Float,
    keyboardRegionHeightPx: Float
): List<KeyRect> {
    val w = keyboardWidthPx
    val h = keyboardRegionHeightPx

    // 水平类换算 × W
    val sideMargin = config.sideMarginRatio * w
    val hGap = config.horizontalGapRatio * w
    // 垂直类换算 × 键盘区高度 H
    val topMargin = config.topMarginRatio * h
    val vGap = config.verticalGapRatio * h
    val keyHeight = config.normalKeyHeightRatio * h

    val inner = w - 2f * sideMargin

    val rects = ArrayList<KeyRect>()
    config.rows.forEachIndexed { rowIndex, row ->
        val rowTop = topMargin + rowIndex * (keyHeight + vGap)

        // 预算各键像素宽度与行内容总宽
        val keyWidths = row.keys.map { it.widthRatio * w }
        val n = keyWidths.size
        val rowContent = keyWidths.sum() + (n - 1).coerceAtLeast(0) * hGap
        val leftover = inner - rowContent
        val rowStartX = sideMargin + leftover / 2f

        // 逐键累加 X 起点
        var x = rowStartX
        row.keys.forEachIndexed { keyIndex, key ->
            val keyWidth = keyWidths[keyIndex]
            rects.add(
                KeyRect(
                    rowIndex = rowIndex,
                    keyIndex = keyIndex,
                    action = key.action,
                    x = x,
                    y = rowTop,
                    width = keyWidth,
                    height = keyHeight
                )
            )
            x += keyWidth + hGap
        }
    }
    return rects
}
