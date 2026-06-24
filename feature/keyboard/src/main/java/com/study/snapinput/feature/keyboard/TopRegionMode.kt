package com.study.snapinput.feature.keyboard

/**
 * 顶部区域（Top_Region）的两种互斥显示模式。
 *
 * 该类型与选择逻辑均为纯 Kotlin（不依赖 Compose），便于在 JVM 上做单元 / 属性测试。
 */
enum class TopRegionMode {
    /** 工具栏：无活动输入（Word_Buffer 为空）时显示。 */
    Toolbar,

    /** 候选词栏：存在活动输入（Word_Buffer 非空）时显示。 */
    PredictionBar
}

/**
 * 根据 Word_Buffer 是否为空选择顶部区域的显示模式（纯函数）。
 *
 * - Word_Buffer 为空 → [TopRegionMode.Toolbar]（Requirement 10.3，以及 10.5 提交/清空后回到工具栏）
 * - Word_Buffer 非空 → [TopRegionMode.PredictionBar]（Requirement 10.4）
 *
 * @param wordBufferEmpty Word_Buffer 是否为空。
 * @return 对应的顶部区域显示模式。
 */
fun selectTopRegionMode(wordBufferEmpty: Boolean): TopRegionMode =
    if (wordBufferEmpty) TopRegionMode.Toolbar else TopRegionMode.PredictionBar
