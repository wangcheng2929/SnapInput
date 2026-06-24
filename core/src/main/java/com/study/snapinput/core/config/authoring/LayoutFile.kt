package com.study.snapinput.core.config.authoring

import kotlinx.serialization.Serializable

/**
 * 布局文件（authoring）：`keyboard/layouts/{layoutId}.json` 的反序列化模型。
 */
@Serializable
data class LayoutFile(
    /** 引用的主题 id。 */
    val theme: String,
    /** 引用的模板文件名列表。 */
    val templates: List<String> = emptyList(),
    /** 行列表：LettersRow / KeysRow / `"$rowTemplateName"`。 */
    val rows: List<RowSpecOrRef>
)
