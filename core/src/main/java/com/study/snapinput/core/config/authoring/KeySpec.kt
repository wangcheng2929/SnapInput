package com.study.snapinput.core.config.authoring

import kotlinx.serialization.Serializable

/**
 * 按键规格（authoring）。字段大多可选，缺省由 Resolver 按
 * `theme.keyDefaults[class] < template < inline` 优先级合并补全。
 */
@Serializable
data class KeySpec(
    /** Key_Class 名；缺省 "letter"（由 Resolver 处理缺省）。 */
    val keyClass: String? = null,
    /** Action_Value。 */
    val action: String? = null,
    /** 固定宽度（数字，(0,1]）；Key_Defaults 与 `$ref` per-site 覆盖使用。 */
    val widthRatio: Float? = null,
    /** 统一宽度声明：Fixed（固定比例）或 Fill（填充，可带权重）；模板常用 "fill"。 */
    val width: AuthoringWidth? = null,
    /** 正常态背景色，`#AARRGGBB`。 */
    val normalBackgroundColor: String? = null,
    /** 按下态背景色，`#AARRGGBB`。 */
    val pressedBackgroundColor: String? = null,
    /** 主文本规格。 */
    val mainText: TextSpec? = null,
    /** 子标签规格。 */
    val subLabel: TextSpec? = null,
    /** 形如 "$name" 的引用解析后写入（由 KeySpecOrRef 体系处理，通常为 null）。 */
    val ref: String? = null
)

/**
 * 文本规格（authoring）：内容、颜色与字号比例，均可选。
 */
@Serializable
data class TextSpec(
    /** 文本内容。 */
    val content: String? = null,
    /** 文本颜色，`#AARRGGBB`。 */
    val color: String? = null,
    /** 字号比例，占正常键高。 */
    val sizeRatio: Float? = null
)
