package com.study.snapinput.core.config.model

import kotlinx.serialization.Serializable

/**
 * 扁平运行时的单个按键配置。
 *
 * [action] 为 Action_Value，语义见设计文档：字面字符、空格 " "、删除 "Del"、换行 "Enter"、
 * 修饰键 "Shift"（不输出）、占位键 "123"/"中/英"（不输出、不切换）。
 */
@Serializable
data class KeyConfig(
    /** 触发行为的 Action_Value。 */
    val action: String,
    /** 按键宽度比例，占键盘宽度 W；合法范围 (0,1]（fill 已在 Resolver 展开为具体值）。 */
    val widthRatio: Float,
    /** 正常态背景色，`#AARRGGBB`。 */
    val normalBackgroundColor: String,
    /** 按下态背景色，`#AARRGGBB`；可选，省略时由 [normalBackgroundColor] 的 RR/GG/BB 各 ×0.8 向下取整、AA 不变推导。 */
    val pressedBackgroundColor: String? = null,
    /** 主文本样式。 */
    val mainText: TextStyleConfig,
    /** 子标签；可选，本轮仅显示、不参与输入。 */
    val subLabel: SubLabel? = null
)
