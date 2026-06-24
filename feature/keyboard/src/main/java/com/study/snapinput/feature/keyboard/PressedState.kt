package com.study.snapinput.feature.keyboard

import androidx.compose.runtime.mutableStateMapOf
import com.study.snapinput.core.config.layout.KeyRect

/**
 * 按下态（Pressed_State）：按 Pointer_Identifier（指针标识）独立跟踪每个触点当前命中的按键。
 *
 * 设计要点（见 Requirement 8.11 / 8.12、Requirement 12 多指）：
 * - 以 `pointerId -> KeyRect` 的映射记录“哪个指针正按在哪个按键上”，支持多指（rollover）独立跟踪。
 * - 内部使用 Compose 快照状态 [mutableStateMapOf]，对其增删会触发读取处（[KeyboardRenderer] 的 Canvas）重绘，
 *   从而实现“按下变色、抬起恢复”的即时视觉反馈。
 *
 * 本类只负责“按下态数据”；指针事件的接入（DOWN/MOVE/UP 仲裁）由 `Modifier.multiTouchKeyboard`
 * 在任务 11.3 中实现并驱动本类。
 */
class PressedState {

    /** pointerId -> 该指针当前命中的按键矩形（快照状态，变化触发重绘）。 */
    private val pressedByPointer = mutableStateMapOf<Int, KeyRect>()

    /**
     * 记录某指针按下并命中 [rect]。
     *
     * @param pointerId 指针标识（Pointer_Identifier）。
     * @param rect 该指针命中的按键矩形。
     */
    fun press(pointerId: Int, rect: KeyRect) {
        pressedByPointer[pointerId] = rect
    }

    /**
     * 清除某指针的按下态（抬起 / 取消）。
     *
     * @param pointerId 指针标识。
     */
    fun release(pointerId: Int) {
        pressedByPointer.remove(pointerId)
    }

    /**
     * 判断 [rect] 当前是否处于按下态（有任一指针正命中它）。
     *
     * @param rect 待查询的按键矩形。
     * @return 若有任一活动指针命中该矩形则为 true。
     */
    fun isPressed(rect: KeyRect): Boolean = pressedByPointer.containsValue(rect)

    /**
     * 当前所有处于按下态的按键矩形快照，供绘制时选择背景色使用；其变化会触发重绘。
     */
    fun pressedRectsSnapshot(): Set<KeyRect> = pressedByPointer.values.toSet()

    /** 当前活动指针数量（供多指上限判断使用，上限 10 见 Requirement 12.7）。 */
    val activePointerCount: Int get() = pressedByPointer.size
}
