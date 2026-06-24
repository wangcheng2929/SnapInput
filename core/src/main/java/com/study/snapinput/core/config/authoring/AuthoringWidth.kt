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
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonPrimitive

/**
 * authoring 层按键宽度：固定比例（[Fixed]）或填充（[Fill]，可带正权重，默认 1）。
 *
 * 仅存在于 authoring 层；Resolver 会把 [Fill] 展开为具体的 Key_Width_Ratio，
 * 运行时扁平 KeyboardConfig 中不存在 fill 概念。
 *
 * 自定义 [AuthoringWidthSerializer] 接受三种 JSON 形态：
 *   - 数字：           `"width": 0.12`        -> [Fixed]`(0.12)`
 *   - 字符串 "fill"：   `"width": "fill"`      -> [Fill]`(weight = 1f)`
 *   - 对象 {"fill": w}：`"width": {"fill": 2}` -> [Fill]`(weight = 2f)`
 */
@Serializable(with = AuthoringWidthSerializer::class)
sealed interface AuthoringWidth {
    /** 固定宽度比例，合法范围 (0,1]。 */
    data class Fixed(val ratio: Float) : AuthoringWidth

    /** 填充宽度，权重 > 0，默认 1；剩余宽度按权重分配。 */
    data class Fill(val weight: Float = 1f) : AuthoringWidth
}

/**
 * [AuthoringWidth] 的自定义序列化器：将数字 / `"fill"` / `{"fill": w}` 三种 JSON 形态
 * 归一化为 [AuthoringWidth.Fixed] 或 [AuthoringWidth.Fill]。
 */
object AuthoringWidthSerializer : KSerializer<AuthoringWidth> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.study.snapinput.core.config.authoring.AuthoringWidth")

    override fun deserialize(decoder: Decoder): AuthoringWidth {
        val input = decoder as? JsonDecoder
            ?: throw IllegalStateException("AuthoringWidth 仅支持 JSON 反序列化")
        return when (val element = input.decodeJsonElement()) {
            is JsonPrimitive -> {
                if (element.isString) {
                    // 字符串形态：仅支持 "fill"
                    require(element.content == "fill") {
                        "字符串宽度仅支持 \"fill\"，实际为 \"${element.content}\""
                    }
                    AuthoringWidth.Fill()
                } else {
                    // 数字形态：固定宽度
                    AuthoringWidth.Fixed(element.float)
                }
            }
            is JsonObject -> {
                // 对象形态：{"fill": w}
                val weight = element["fill"]?.jsonPrimitive?.float
                    ?: throw IllegalStateException("宽度对象必须包含数字字段 \"fill\"")
                AuthoringWidth.Fill(weight)
            }
            else -> throw IllegalStateException("无法解析的宽度形态：$element")
        }
    }

    override fun serialize(encoder: Encoder, value: AuthoringWidth) {
        val output = encoder as? JsonEncoder
            ?: throw IllegalStateException("AuthoringWidth 仅支持 JSON 序列化")
        when (value) {
            // 固定宽度序列化为数字
            is AuthoringWidth.Fixed -> output.encodeJsonElement(JsonPrimitive(value.ratio))
            // 填充宽度序列化为 {"fill": weight}
            is AuthoringWidth.Fill ->
                output.encodeJsonElement(buildJsonObject { put("fill", JsonPrimitive(value.weight)) })
        }
    }
}
