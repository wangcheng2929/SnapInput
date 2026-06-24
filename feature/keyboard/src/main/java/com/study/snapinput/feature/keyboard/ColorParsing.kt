package com.study.snapinput.feature.keyboard

import androidx.compose.ui.graphics.Color

/**
 * 渲染期颜色解析工具。
 *
 * 用于在自绘渲染层将配置中的 "#AARRGGBB" 十六进制 ARGB 字符串解析为 Compose [Color]。
 * 当输入格式非法时返回 null，供渲染期颜色容错使用（参见 Requirement 8.13：
 * 非法颜色时改用 Default_Config 对应颜色渲染受影响文字）。
 */
object ColorParsing {

    /** 合法的 "#AARRGGBB" 格式：井号 + 8 位十六进制（大小写不敏感）。 */
    private val ARGB_REGEX = Regex("^#[0-9A-Fa-f]{8}$")

    /**
     * 将 "#AARRGGBB" 字符串解析为 Compose [Color]。
     *
     * @param argb 形如 "#AARRGGBB" 的十六进制 ARGB 字符串，AA/RR/GG/BB 各两位十六进制数字，大小写不敏感。
     * @return 解析得到的 [Color]；若格式非法则返回 null。
     */
    fun parseArgbColor(argb: String): Color? {
        // 先校验格式，避免对非法输入抛出异常
        if (!ARGB_REGEX.matches(argb)) return null

        // 去掉前导 '#' 后按 ARGB 顺序拆分各分量
        val alpha = argb.substring(1, 3).toInt(16)
        val red = argb.substring(3, 5).toInt(16)
        val green = argb.substring(5, 7).toInt(16)
        val blue = argb.substring(7, 9).toInt(16)

        return Color(red = red, green = green, blue = blue, alpha = alpha)
    }
}
