package com.study.snapinput.feature.keyboard

/**
 * Shift / Caps Lock 的三态模式。
 *
 * - [None]：普通模式，字母按小写发射。
 * - [ShiftOnce]：一次性大写，仅作用于其后第一个被触发的字母（发射后归零）。
 * - [CapsLock]：持续大写，直到再次单击 Shift 取消。
 */
sealed interface ShiftMode {
    object None : ShiftMode
    object ShiftOnce : ShiftMode
    object CapsLock : ShiftMode
}

/**
 * Shift / Caps Lock 状态机（纯 JVM 逻辑，不依赖 Compose）。
 *
 * 语义（对应 Requirements 11.10–11.13）：
 * - 单击 Shift → 进入 [ShiftMode.ShiftOnce]（临时大写）。
 * - 在 [DOUBLE_TAP_THRESHOLD_MS] 毫秒内双击 Shift → 进入 [ShiftMode.CapsLock]（持续大写）。
 * - 处于 [ShiftMode.CapsLock] 时单击 Shift → 取消，回到 [ShiftMode.None]。
 * - shift 或 caps lock 任一激活时，字母按大写发射；否则小写。
 *
 * 该类持有可变状态，应由单一输入线程顺序调用。
 */
class ShiftState {

    /** 当前 Shift / Caps 模式，供渲染与发射逻辑读取。 */
    var mode: ShiftMode = ShiftMode.None
        private set

    /** 上一次 Shift 单击的时间戳（毫秒）；尚未点击时为 null。 */
    private var lastTapMs: Long? = null

    /**
     * 处理一次 Shift 按键单击。
     *
     * @param nowMs 本次点击的时间戳（毫秒，单调递增）。
     */
    fun onShiftTap(nowMs: Long) {
        val previousTap = lastTapMs
        // 与上一次点击间隔在阈值内视为双击。
        val isDoubleTap = previousTap != null && (nowMs - previousTap) <= DOUBLE_TAP_THRESHOLD_MS

        mode = when {
            // CapsLock 下任意单击都取消 caps lock（优先于双击判定）。
            mode == ShiftMode.CapsLock -> ShiftMode.None
            // 300ms 内双击进入 caps lock。
            isDoubleTap -> ShiftMode.CapsLock
            // 普通单击进入一次性大写。
            else -> ShiftMode.ShiftOnce
        }
        lastTapMs = nowMs
    }

    /**
     * 按当前模式转换字面字母：[ShiftMode.ShiftOnce] 或 [ShiftMode.CapsLock] 时大写，否则小写。
     *
     * @param raw 字母按键的原始 Action_Value。
     * @return 处理后的字母。
     */
    fun transformLetter(raw: String): String =
        if (mode == ShiftMode.ShiftOnce || mode == ShiftMode.CapsLock) {
            raw.uppercase()
        } else {
            raw.lowercase()
        }

    /**
     * 在一个字母被发射后调用：
     * [ShiftMode.ShiftOnce] 归零为 [ShiftMode.None]；[ShiftMode.CapsLock] 与 [ShiftMode.None] 保持不变。
     */
    fun afterLetterEmitted() {
        if (mode == ShiftMode.ShiftOnce) {
            mode = ShiftMode.None
        }
    }

    companion object {
        /** 双击判定阈值（毫秒）。 */
        const val DOUBLE_TAP_THRESHOLD_MS: Long = 300L
    }
}
