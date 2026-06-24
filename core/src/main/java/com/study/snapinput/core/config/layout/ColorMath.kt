package com.study.snapinput.core.config.layout

/**
 * 由正常背景色推导按下态背景色（纯函数）。
 *
 * 规则：将 RR、GG、BB 三个分量各自乘以 0.8 并向下取整，AA 分量保持不变；
 * 结果重新格式化为 `#AARRGGBB`（大写十六进制、每段补足两位）。
 *
 * 入参须为合法的 `#AARRGGBB` 字符串（AA/RR/GG/BB 各两位十六进制，大小写不敏感）。
 *
 * @param normalArgb 正常背景色，形如 `#AARRGGBB`。
 * @return 推导出的按下态背景色，形如 `#AARRGGBB`（大写）。
 * @throws IllegalArgumentException 入参不符合 `#AARRGGBB` 格式时。
 */
fun derivePressedColor(normalArgb: String): String {
    require(normalArgb.length == 9 && normalArgb[0] == '#') {
        "颜色格式非法，期望 #AARRGGBB：$normalArgb"
    }

    // 解析各分量（两位十六进制）。
    val aa = normalArgb.substring(1, 3).toInt(16)
    val rr = normalArgb.substring(3, 5).toInt(16)
    val gg = normalArgb.substring(5, 7).toInt(16)
    val bb = normalArgb.substring(7, 9).toInt(16)

    // RR/GG/BB 各 ×0.8 向下取整，AA 不变。
    val pressedRr = (rr * 0.8).toInt()
    val pressedGg = (gg * 0.8).toInt()
    val pressedBb = (bb * 0.8).toInt()

    return buildString {
        append('#')
        append(toTwoHex(aa))
        append(toTwoHex(pressedRr))
        append(toTwoHex(pressedGg))
        append(toTwoHex(pressedBb))
    }
}

/** 将 0–255 的整数格式化为两位大写十六进制字符串。 */
private fun toTwoHex(value: Int): String =
    value.toString(16).uppercase().padStart(2, '0')
