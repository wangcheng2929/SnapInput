package com.study.snapinput.feature.keyboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.study.snapinput.core.config.DefaultConfig
import com.study.snapinput.core.config.layout.KeyRect
import com.study.snapinput.core.config.layout.computeKeyRects
import com.study.snapinput.core.config.layout.derivePressedColor
import com.study.snapinput.core.config.model.KeyConfig
import com.study.snapinput.core.config.model.KeyboardConfig

/**
 * 键盘区（Keyboard_Region）单画布自绘渲染器，替代旧的“每键一个 Composable”的组合方式。
 *
 * 在**单个** [Canvas] 上完成一次绘制 pass：
 * 1. 以 [computeKeyRects] 计算所有按键的像素矩形（绘制原点与命中测试的单一真相源）。
 * 2. 对每个矩形 `drawRoundRect` 绘制圆角背景（圆角为固定 dp 转像素，不随分辨率缩放）。
 * 3. 用原生 Paint `drawText` 绘制主文本（居中）与可选子标签（主文本上方）。
 * 4. 处于按下态的按键改用 Pressed_Background_Color（配置的或由正常色推导）。
 *
 * 容错（健壮回退）：
 * - 配置为 0 行（[KeyboardConfig.rows] 为空）→ 改用 [DefaultConfig.config]（Requirement 8.14）。
 * - 渲染期遇到非法颜色（无法解析为 `#AARRGGBB`）→ 改用 [DefaultConfig] 对应按键的颜色，
 *   仍绘制该按键其余内容（Requirement 8.13）。
 * - [usingFallback] 为 true → 顶部叠加一条可见回退提示（Requirement 6.4）。
 *
 * 字号约定：主文本字号 = Main_Text_Size_Ratio × 正常键高像素；子标签字号 = Sub_Label_Size_Ratio × 正常键高像素；
 * 均为像素值、不使用 sp、不跟随系统字号缩放（Requirement 7.10 / 7.11 / 7.12）。
 *
 * 注意：本任务（11.2）只负责渲染与暴露 [onAction] 供后续接线；多指指针事件（DOWN 命中即发射、
 * MOVE/UP 不重发、上限 10）由 `Modifier.multiTouchKeyboard` 在任务 11.3 中接入并驱动 [PressedState]。
 *
 * @param config 已展开的扁平运行时配置。
 * @param usingFallback 是否正使用回退配置（用于显示可见提示）。
 * @param onAction 按键触发时上抛已处理的 Action_Value（本任务暂不在内部触发，留待 11.3 指针接线）。
 * @param modifier 外部修饰符。
 * @param pressed 按下态来源；为 null 时内部 [remember] 一个，便于 11.3 由宿主/Modifier 共享同一实例。
 */
@Composable
fun KeyboardRenderer(
    config: KeyboardConfig,
    usingFallback: Boolean,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
    pressed: PressedState? = null
) {
    // 0 行配置回退到内置默认配置（Requirement 8.14）。
    val effectiveConfig = if (config.rows.isEmpty()) DefaultConfig.config else config
    // 按下态：优先使用外部传入实例（供 11.3 指针接线共享），否则内部记忆一个。
    val pressedState = pressed ?: remember { PressedState() }
    // 圆角固定 dp → 像素（不随分辨率缩放，Requirement 7.13 / 8.6）。
    val cornerRadiusPx = with(LocalDensity.current) { effectiveConfig.cornerRadiusDp.dp.toPx() }

    // 画布像素尺寸由 onSizeChanged 回填；矩形需在 Composable 层可见，
    // 既供 Modifier.multiTouchKeyboard 做命中测试，又供 Canvas 绘制——保持单一真相源。
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    // 尺寸或配置变化时重算按键矩形；尚未测量（尺寸为 0）时为空列表。
    val rects = remember(effectiveConfig, canvasSize) {
        if (canvasSize.width == 0 || canvasSize.height == 0) {
            emptyList()
        } else {
            computeKeyRects(
                effectiveConfig,
                canvasSize.width.toFloat(),
                canvasSize.height.toFloat()
            )
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            // 接入原始多指事件：DOWN 命中即通过 onAction 上抛 Action_Value，并驱动按下态重绘（任务 11.3）。
            .multiTouchKeyboard(
                rects = rects,
                pressed = pressedState,
                onActionDown = { rect -> onAction(rect.action) }
            )
    ) {
        val h = size.height
        val keyHeightPx = effectiveConfig.normalKeyHeightRatio * h
        // 读取按下态快照（其变化触发重绘）。
        val pressedRects = pressedState.pressedRectsSnapshot()

        rects.forEach { rect ->
            val key = effectiveConfig.rows.getOrNull(rect.rowIndex)
                ?.keys?.getOrNull(rect.keyIndex) ?: return@forEach
            val defaultKey = defaultKeyAt(rect.rowIndex, rect.keyIndex)
            val isPressed = rect in pressedRects

            drawKey(
                rect = rect,
                key = key,
                defaultKey = defaultKey,
                isPressed = isPressed,
                cornerRadiusPx = cornerRadiusPx,
                keyHeightPx = keyHeightPx
            )
        }

        // 回退提示（Requirement 6.4）：顶部叠加一条可见横幅。
        if (usingFallback) {
            drawFallbackHint(size.width, h)
        }
    }
}

/** 取 [DefaultConfig] 中相同 (行,列) 下标的按键，用于渲染期颜色容错的回退取值。 */
private fun defaultKeyAt(rowIndex: Int, keyIndex: Int): KeyConfig? =
    DefaultConfig.config.rows.getOrNull(rowIndex)?.keys?.getOrNull(keyIndex)

/** 绘制单个按键：圆角背景 + 主文本 + 可选子标签。 */
private fun DrawScope.drawKey(
    rect: KeyRect,
    key: KeyConfig,
    defaultKey: KeyConfig?,
    isPressed: Boolean,
    cornerRadiusPx: Float,
    keyHeightPx: Float
) {
    // —— 背景色（按下态优先取 Pressed_Background_Color）——
    val backgroundColor = if (isPressed) {
        resolvePressedBackground(key, defaultKey)
    } else {
        resolveColor(
            argb = key.normalBackgroundColor,
            fallbackArgb = defaultKey?.normalBackgroundColor,
            hardFallback = Color.White
        )
    }
    drawRoundRect(
        color = backgroundColor,
        topLeft = Offset(rect.x, rect.y),
        size = Size(rect.width, rect.height),
        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
    )

    val centerX = rect.x + rect.width / 2f
    val hasSubLabel = key.subLabel != null

    // —— 主文本 ——
    val mainColor = resolveColor(
        argb = key.mainText.color,
        fallbackArgb = defaultKey?.mainText?.color,
        hardFallback = Color.Black
    )
    val mainSizePx = key.mainText.sizeRatio * keyHeightPx
    // 有子标签时主文本下移到按键下部，子标签置于上部；否则主文本垂直居中。
    val mainCenterY = if (hasSubLabel) rect.y + rect.height * 0.62f else rect.y + rect.height / 2f
    drawCenteredText(
        text = key.mainText.content,
        centerX = centerX,
        centerY = mainCenterY,
        textSizePx = mainSizePx,
        color = mainColor
    )

    // —— 子标签（主文本上方）——
    key.subLabel?.let { sub ->
        val subColor = resolveColor(
            argb = sub.color,
            fallbackArgb = defaultKey?.subLabel?.color,
            hardFallback = Color.Black
        )
        val subSizePx = sub.sizeRatio * keyHeightPx
        drawCenteredText(
            text = sub.content,
            centerX = centerX,
            centerY = rect.y + rect.height * 0.28f,
            textSizePx = subSizePx,
            color = subColor
        )
    }
}

/**
 * 解析按下态背景色：
 * - 优先使用配置的 Pressed_Background_Color；
 * - 否则在正常色合法时由其推导（RR/GG/BB ×0.8）；
 * - 解析失败时回退到 [DefaultConfig] 对应按键的颜色，最终硬回退为浅灰。
 */
private fun resolvePressedBackground(key: KeyConfig, defaultKey: KeyConfig?): Color {
    val pressedArgb: String? = key.pressedBackgroundColor
        ?: key.normalBackgroundColor
            .takeIf { ColorParsing.parseArgbColor(it) != null }
            ?.let { derivePressedColor(it) }

    // 默认按键的按下态回退值：同样优先取其 pressed，否则由其正常色推导。
    val defaultFallbackArgb: String? = defaultKey?.let { dk ->
        dk.pressedBackgroundColor
            ?: dk.normalBackgroundColor
                .takeIf { ColorParsing.parseArgbColor(it) != null }
                ?.let { derivePressedColor(it) }
    }

    return resolveColor(
        argb = pressedArgb,
        fallbackArgb = defaultFallbackArgb,
        hardFallback = Color.LightGray
    )
}

/**
 * 渲染期颜色容错（Requirement 8.13）：依次尝试 [argb] → [fallbackArgb] → [hardFallback]。
 * 任一为合法 `#AARRGGBB` 即返回，全部非法时返回硬回退色。
 */
private fun resolveColor(argb: String?, fallbackArgb: String?, hardFallback: Color): Color {
    argb?.let { ColorParsing.parseArgbColor(it)?.let { c -> return c } }
    fallbackArgb?.let { ColorParsing.parseArgbColor(it)?.let { c -> return c } }
    return hardFallback
}

/** 在 (centerX, centerY) 处以原生 Paint 居中绘制一行文本（水平居中、垂直居中）。 */
private fun DrawScope.drawCenteredText(
    text: String,
    centerX: Float,
    centerY: Float,
    textSizePx: Float,
    color: Color
) {
    if (text.isEmpty() || textSizePx <= 0f) return
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        this.color = color.toArgb()
        textSize = textSizePx
        textAlign = android.graphics.Paint.Align.CENTER
    }
    // 由字体度量将“中心 Y”换算为基线 Y，实现垂直居中。
    val fm = paint.fontMetrics
    val baseline = centerY - (fm.ascent + fm.descent) / 2f
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawText(text, centerX, baseline, paint)
    }
}

/** 顶部可见回退提示横幅（Requirement 6.4）。 */
private fun DrawScope.drawFallbackHint(width: Float, height: Float) {
    val bannerHeight = (height * 0.07f).coerceAtLeast(1f)
    drawRect(
        color = Color(0xCCFF6D00),
        topLeft = Offset(0f, 0f),
        size = Size(width, bannerHeight)
    )
    drawCenteredText(
        text = "使用默认回退配置",
        centerX = width / 2f,
        centerY = bannerHeight / 2f,
        textSizePx = bannerHeight * 0.55f,
        color = Color.White
    )
}
