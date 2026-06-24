package com.study.snapinput.core.config.authoring

import kotlinx.serialization.Serializable

/**
 * 模板文件（authoring）：`keyboard/templates/{name}.json` 的反序列化模型。
 *
 * 含按键模板与行模板，供 layout 通过 `"$name"` / `{"$ref": ...}` 引用。
 */
@Serializable
data class TemplateFile(
    /** 按键模板：名称 -> 按键规格。 */
    val keyTemplates: Map<String, KeySpec> = emptyMap(),
    /** 行模板：名称 -> 行规格。 */
    val rowTemplates: Map<String, RowSpec> = emptyMap()
)
