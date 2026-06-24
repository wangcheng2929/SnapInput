package com.study.snapinput.core.config.authoring

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/**
 * `keys` / `lead` / `trail` 条目：模板引用（可带覆盖）或内联规格。
 *
 * JSON 形态：
 *   - 字符串 `"$name"`           -> [KeyTemplateRef]`("name", overrides = null)`
 *   - 对象 `{ "$ref": "n", ... }` -> [KeyTemplateRef]`("n", overrides = 其余字段构成的 KeySpec)`
 *   - 其他对象（内联规格）        -> [InlineKey]`(KeySpec)`
 */
@Serializable(with = KeySpecOrRefSerializer::class)
sealed interface KeySpecOrRef

/** 模板引用（可带 per-site 覆盖）。由 `"$name"` 字符串或 `{"$ref": ...}` 对象解析。 */
data class KeyTemplateRef(val name: String, val overrides: KeySpec? = null) : KeySpecOrRef

/** 内联按键规格。 */
data class InlineKey(val spec: KeySpec) : KeySpecOrRef

/**
 * [KeySpecOrRef] 的自定义序列化器，处理 `"$name"`、`{"$ref": ...}` 与内联三种 JSON 形态。
 */
object KeySpecOrRefSerializer : KSerializer<KeySpecOrRef> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.study.snapinput.core.config.authoring.KeySpecOrRef")

    override fun deserialize(decoder: Decoder): KeySpecOrRef {
        val input = decoder as? JsonDecoder
            ?: throw IllegalStateException("KeySpecOrRef 仅支持 JSON 反序列化")
        return when (val element = input.decodeJsonElement()) {
            is JsonPrimitive -> {
                require(element.isString) { "按键引用必须为字符串，实际为 $element" }
                val raw = element.content
                require(raw.startsWith("$")) { "按键模板引用必须以 \$ 开头：$raw" }
                // "$name" 形态：去掉前导 $ 作为模板名，无覆盖
                KeyTemplateRef(name = raw.substring(1), overrides = null)
            }
            is JsonObject -> {
                val refNode = element["\$ref"]
                if (refNode != null) {
                    // {"$ref": "name", ...覆盖字段} 形态
                    val rawName = (refNode as JsonPrimitive).content
                    // 兼容引用名带或不带前导 $
                    val name = if (rawName.startsWith("$")) rawName.substring(1) else rawName
                    val overrideEntries = element.filterKeys { it != "\$ref" }
                    val overrides = if (overrideEntries.isEmpty()) {
                        null
                    } else {
                        input.json.decodeFromJsonElement(
                            KeySpec.serializer(),
                            JsonObject(overrideEntries)
                        )
                    }
                    KeyTemplateRef(name = name, overrides = overrides)
                } else {
                    // 内联按键规格
                    InlineKey(input.json.decodeFromJsonElement(KeySpec.serializer(), element))
                }
            }
            else -> throw IllegalStateException("无法解析的按键条目形态：$element")
        }
    }

    override fun serialize(encoder: Encoder, value: KeySpecOrRef) {
        val output = encoder as? JsonEncoder
            ?: throw IllegalStateException("KeySpecOrRef 仅支持 JSON 序列化")
        when (value) {
            is KeyTemplateRef -> {
                val overrides = value.overrides
                if (overrides == null) {
                    // 无覆盖：序列化为 "$name" 字符串
                    output.encodeJsonElement(JsonPrimitive("\$${value.name}"))
                } else {
                    // 带覆盖：序列化为 {"$ref": "name", ...覆盖字段}
                    val overrideObject =
                        output.json.encodeToJsonElement(KeySpec.serializer(), overrides).jsonObject
                    output.encodeJsonElement(
                        buildJsonObject {
                            put("\$ref", JsonPrimitive(value.name))
                            for ((k, v) in overrideObject) put(k, v)
                        }
                    )
                }
            }
            // 内联规格
            is InlineKey ->
                output.encodeJsonElement(output.json.encodeToJsonElement(KeySpec.serializer(), value.spec))
        }
    }
}
