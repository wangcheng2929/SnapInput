package com.study.snapinput.feature.keyboard

import com.study.snapinput.core.config.layout.KeyRect
import com.study.snapinput.core.config.layout.hitTest

/**
 * 多指仲裁纯逻辑（不依赖 Compose 运行时，可在 JVM 上做单元 / 属性测试）。
 *
 * 该类把 [Modifier.multiTouchKeyboard] 中的命中与发射判定抽出为纯状态机，
 * Composable 仅负责把原始 `pointerInput` 事件转译为对本类的调用、并消费其返回结果。
 *
 * 语义（对应 Requirements 12.2、12.3、12.5、12.6、12.7）：
 * - 按 [Pointer_Identifier][pointerId] 对每个 Touch_Point 独立执行命中测试并独立触发（12.3）。
 * - touch-down 落入某按键矩形时，对该按键“发射恰好一次”（由 [onDown] 返回命中的 [KeyRect] 表达）（12.1/12.3）。
 * - touch-up（[onUp]）与 move（[onMove]）绝不重新发射（12.2/12.5）。
 * - touch-down 落在间距 / 外边距（[hitTest] 返回 null）时忽略，不跟踪、不发射，且不影响其他指针（12.4）。
 * - 最多同时跟踪 [maxPointers] 个 Touch_Point（默认 10）（12.6）。
 * - 已满载时额外的 touch-down 被忽略，不发射，且不影响已跟踪指针（12.7）。
 *
 * 该类持有可变状态，应由单一输入线程顺序调用。
 *
 * @param maxPointers 同时跟踪的活动指针上限，默认 10。
 */
class MultiTouchArbiter(private val maxPointers: Int = DEFAULT_MAX_POINTERS) {

    /** pointerId -> 该指针当前按下命中的按键矩形（用于渲染按下态与去重）。 */
    private val tracked = LinkedHashMap<Int, KeyRect>()

    /**
     * 处理一次 touch-down。
     *
     * 仅在满足以下全部条件时登记该指针并返回命中的 [KeyRect]（表示“此刻发射一次”）：
     * - [hitTest] 命中某按键（落在间距 / 外边距时返回 null，忽略）；
     * - 该 [pointerId] 尚未被跟踪（重复 DOWN 不重发）；
     * - 当前活动指针数小于 [maxPointers]（满载时额外 DOWN 被忽略）。
     *
     * @param pointerId 指针标识。
     * @param px 触摸点 X（键盘区左上角为原点，像素）。
     * @param py 触摸点 Y（键盘区左上角为原点，像素）。
     * @param rects 当前键盘的全部按键矩形。
     * @return 命中并需发射的 [KeyRect]；被忽略（间距/外边距、重复、满载）时返回 null。
     */
    fun onDown(pointerId: Int, px: Float, py: Float, rects: List<KeyRect>): KeyRect? {
        // 已跟踪的指针重复 DOWN：不重发、不改动状态。
        if (tracked.containsKey(pointerId)) return null
        // 满载：忽略额外 DOWN，不影响已跟踪指针。
        if (tracked.size >= maxPointers) return null

        // 命中测试：落在间距 / 外边距返回 null，直接忽略不跟踪。
        val hit = hitTest(rects, px, py) ?: return null

        tracked[pointerId] = hit
        return hit
    }

    /**
     * 处理一次 move。仅用于（在 Composable 侧）刷新坐标语义，本纯逻辑下绝不重新发射。
     *
     * 即使指针移动到另一按键或移出键盘区，也不更新命中、不发射（Requirement 12.5）。
     *
     * @param pointerId 指针标识。
     */
    fun onMove(pointerId: Int) {
        // 故意为空：移动不改变已登记的命中，也不发射任何 Action_Value。
    }

    /**
     * 处理一次 touch-up / cancel：移除该指针的跟踪，仅清除按下态视觉，不发射。
     *
     * @param pointerId 指针标识。
     * @return 抬起前该指针所命中的按键矩形（供调用方清除对应按下态）；未跟踪时返回 null。
     */
    fun onUp(pointerId: Int): KeyRect? = tracked.remove(pointerId)

    /** 当前活动（处于按下状态）的指针数量。 */
    fun activePointerCount(): Int = tracked.size

    /** 指定指针是否正在被跟踪。 */
    fun isTracked(pointerId: Int): Boolean = tracked.containsKey(pointerId)

    /**
     * 当前所有处于按下态的按键矩形快照（供渲染按下态使用）。
     *
     * 返回的是只读副本，不随后续状态变化而改变。
     */
    fun pressedRects(): List<KeyRect> = tracked.values.toList()

    companion object {
        /** 默认活动指针上限（Requirement 12.6）。 */
        const val DEFAULT_MAX_POINTERS: Int = 10
    }
}
