package com.study.snapinput.feature.keyboard

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import com.study.snapinput.core.config.layout.KeyRect

/**
 * 多指键盘指针接入修饰符：把底层原始 `pointerInput` 事件转译为对 [MultiTouchArbiter] 的调用，
 * 并据其返回结果驱动 [PressedState] 重绘、在 touch-down 命中时上抛 Action_Value。
 *
 * 行为契约（对应 Requirements 11.15、12.1、12.2、12.3）：
 * - 每个 Touch_Point 按 [Pointer_Identifier][androidx.compose.ui.input.pointer.PointerId] 独立做命中与触发（12.3）。
 * - touch-down 落入某按键矩形时，对该按键“发射恰好一次”：调用 [onActionDown] 并登记按下态（12.1/12.3）。
 * - move（保持按下移动）与 touch-up 绝不重新发射（12.2）：move 仅转交仲裁器（不改命中），up 仅清除按下态。
 * - 落在间距 / 外边距、重复 DOWN、或活动指针已达上限（默认 10）时由 [MultiTouchArbiter] 忽略，不发射。
 *
 * 实现要点：
 * - [MultiTouchArbiter] 在 `pointerInput` 协程块内创建，跨整个事件循环复用，从而保持多指跟踪状态。
 * - 以 [rects] 作为 `pointerInput` 的 key：键盘矩形（尺寸/配置）变化时重启事件循环并重建仲裁器。
 * - [androidx.compose.ui.input.pointer.PointerId] 的 value 为 Long，这里统一用 `.toInt()` 转换；
 *   同一手势内转换稳定一致，足以作为仲裁器与按下态的 pointerId 键。
 *
 * @param rects 当前键盘的全部按键矩形（命中测试与绘制的单一真相源）。
 * @param pressed 按下态，命中/抬起时由本修饰符更新以触发重绘。
 * @param onActionDown touch-down 命中某按键时回调（每次命中恰好一次），用于上抛 Action_Value。
 */
fun Modifier.multiTouchKeyboard(
    rects: List<KeyRect>,
    pressed: PressedState,
    onActionDown: (KeyRect) -> Unit
): Modifier = this.pointerInput(rects) {
    // 仲裁器在事件循环外创建，跨循环复用以保持多指跟踪状态（满载 / 去重 / 命中由其裁决）。
    val arbiter = MultiTouchArbiter()
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            event.changes.forEach { change ->
                // 同一手势内 Long→Int 转换稳定一致，作为仲裁器与按下态的指针键。
                val pointerId = change.id.value.toInt()
                when {
                    // touch-down：交由仲裁器裁决；命中则登记按下态并发射恰好一次。
                    change.changedToDown() -> {
                        val hit = arbiter.onDown(
                            pointerId = pointerId,
                            px = change.position.x,
                            py = change.position.y,
                            rects = rects
                        )
                        if (hit != null) {
                            pressed.press(pointerId, hit)
                            onActionDown(hit)
                        }
                        change.consume()
                    }
                    // touch-up：仅清除该指针按下态，绝不重新发射。
                    change.changedToUp() -> {
                        arbiter.onUp(pointerId)
                        pressed.release(pointerId)
                        change.consume()
                    }
                    // 保持按下的 move：仅转交仲裁器（不改命中、不发射）。
                    change.pressed -> {
                        arbiter.onMove(pointerId)
                    }
                }
            }
        }
    }
}
