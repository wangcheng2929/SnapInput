package com.study.snapinput.core.config.model

import kotlinx.serialization.Serializable

/**
 * 扁平运行时的一行键盘配置。
 */
@Serializable
data class RowConfig(
    /** 本行的按键列表；按键数合法范围 1..32。 */
    val keys: List<KeyConfig>
)
