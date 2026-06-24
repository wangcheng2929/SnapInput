package com.study.snapinput.core.config.authoring

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * `rows` 条目：行模板引用（[RowTemplateRef]）或行规格（[RowSpec]）。
 *
 * JSON 形态：`"$name"` 字符串解析为 [RowTemplateRef]；对象解析为 [RowSpec]。
 */
@Serializable(with = RowSpecOrRefSerializer::class)
sealed interface RowSpecOrRef

/** 行模板引用，由 `"$name"` 字符串解析。 */
data class RowTemplateRef(val name: String) : RowSpecOrRef

/**
 * 行规格：字母行简写（[LettersRow]）或显式键列表（[KeysRow]）。
 */
@Serializable(with = RowSpecSerializer::class)
sealed interface RowSpec : RowSpecOrRef {

    /**
     * 字母行简写：[letters] 批量展开为 letter 键；可带与字母等长的 [subLabels]
     * 与 [lead] / [trail] 特殊键。
     */
    @Serializable
    data class LettersRow(
        val letters: String,
        /** JSON 可为字符串（逐字符）或数组；反序列化归一化为 List。 */
        @Serializable(with = SubLabelsSerializer::class)
        val subLabels: List<String>? = null,
        val lead: KeySpecOrRef? = null,
        val trail: KeySpecOrRef? = null,
        val keyClass: String = "letter"
    ) : RowSpec

    /** 显式行：keys 列表。 */
    @Serializable
    data class KeysRow(val keys: List<KeySpecOrRef>) : RowSpec
}

/**
 * [RowSpecOrRef] 的自定义序列化器：`"$name"` 字符串 -> [RowTemplateRef]，对象 -> [RowSpec]。
 */
object RowSpecOrRefSerializer : KSerializer<RowSpecOrRef> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.study.snapinput.core.config.authoring.RowSpecOrRef")

    override fun deserialize(decoder: Decoder): RowSpecOrRef {
        val input = decoder as? JsonDecoder
            ?: throw IllegalStateException("RowSpecOrRef 仅支持 JSON 反序列化")
        return when (val element = input.decodeJsonElement()) {
            is JsonPrimitive -> {
                require(element.isString) { "行条目必须为字符串引用或对象，实际为 $element" }
                val raw = element.content
                require(raw.startsWith("$")) { "行模板引用必须以 \$ 开头：$raw" }
                RowTemplateRef(name = raw.substring(1))
            }
            is JsonObject -> input.json.decodeFromJsonElement(RowSpecSerializer, element)
            else -> throw IllegalStateException("无法解析的行条目形态：$element")
        }
    }

    override fun serialize(encoder: Encoder, value: RowSpecOrRef) {
        val output = encoder as? JsonEncoder
            ?: throw IllegalStateException("RowSpecOrRef 仅支持 JSON 序列化")
        when (value) {
            is RowTemplateRef -> output.encodeJsonElement(JsonPrimitive("\$${value.name}"))
            is RowSpec -> output.encodeJsonElement(
                output.json.encodeToJsonElement(RowSpecSerializer, value)
            )
        }
    }
}

/**
 * [RowSpec] 的自定义序列化器：含 `keys` 字段 -> [RowSpec.KeysRow]，否则 -> [RowSpec.LettersRow]。
 */
object RowSpecSerializer : KSerializer<RowSpec> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.study.snapinput.core.config.authoring.RowSpec")

    override fun deserialize(decoder: Decoder): RowSpec {
        val input = decoder as? JsonDecoder
            ?: throw IllegalStateException("RowSpec 仅支持 JSON 反序列化")
        val obj = input.decodeJsonElement().jsonObject
        return if (obj.containsKey("keys")) {
            input.json.decodeFromJsonElement(RowSpec.KeysRow.serializer(), obj)
        } else {
            input.json.decodeFromJsonElement(RowSpec.LettersRow.serializer(), obj)
        }
    }

    override fun serialize(encoder: Encoder, value: RowSpec) {
        val output = encoder as? JsonEncoder
            ?: throw IllegalStateException("RowSpec 仅支持 JSON 序列化")
        val element = when (value) {
            is RowSpec.KeysRow ->
                output.json.encodeToJsonElement(RowSpec.KeysRow.serializer(), value)
            is RowSpec.LettersRow ->
                output.json.encodeToJsonElement(RowSpec.LettersRow.serializer(), value)
        }
        output.encodeJsonElement(element)
    }
}

/**
 * `subLabels` 字段序列化器：JSON 字符串（逐字符）或数组均归一化为 `List<String>`。
 */
object SubLabelsSerializer : KSerializer<List<String>> {
    private val delegate = ListSerializer(String.serializer())

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<String> {
        val input = decoder as? JsonDecoder
            ?: throw IllegalStateException("subLabels 仅支持 JSON 反序列化")
        return when (val element = input.decodeJsonElement()) {
            // 数组形态：逐项取内容
            is JsonArray -> element.map { (it as JsonPrimitive).content }
            // 字符串形态：逐字符拆分
            is JsonPrimitive -> {
                require(element.isString) { "subLabels 字符串形态必须为字符串，实际为 $element" }
                element.content.map { it.toString() }
            }
            else -> throw IllegalStateException("无法解析的 subLabels 形态：$element")
        }
    }

    override fun serialize(encoder: Encoder, value: List<String>) {
        delegate.serialize(encoder, value)
    }
}
