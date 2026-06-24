package com.study.snapinput.core.config.model

import kotlinx.serialization.Serializable

/**
 * 主文本样式：内容、颜色与相对正常键高的字号比例。
 */
@Serializable
data class TextStyleConfig(
    /** 文本内容；长度合法范围 1..32 字符。 */
    val content: String,
    /** 文本颜色，`#AARRGGBB`。 */
    val color: String,
    /** 字号比例，占正常键高；合法范围 (0,5]。 */
    val sizeRatio: Float
)
