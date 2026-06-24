package com.study.snapinput.feature.keyboard

/**
 * 按键发射决策的结果（纯领域类型，不依赖 Compose 或 Android）。
 *
 * 该结果描述“某个按键被触发时应当发生什么”，由宿主层（IME_Service）解释执行：
 * - [Commit]：向输入连接提交一段字面文本（字母 / 字符 / 空格 / 逗号）。
 * - [ControlAction]：执行一个控制动作（如 Del 删除、Enter 换行），由宿主解释。
 * - [NoOp]：不产生任何输出（Shift 修饰键、123 / 中/英 占位键）。
 */
sealed interface EmitResult {

    /** 提交字面文本：字面字符（经 shift/caps 处理）、空格 " "、逗号 "，"。 */
    data class Commit(val text: String) : EmitResult

    /** 控制动作：本轮为 "Del"（删除）与 "Enter"（换行），不直接提交文本。 */
    data class ControlAction(val name: String) : EmitResult

    /** 无操作：修饰键（Shift）与占位键（123、中/英），既不输出也不切换。 */
    object NoOp : EmitResult
}

/**
 * 按键发射决策纯逻辑（不依赖 Compose / Android / InputConnection）。
 *
 * 决策仅依据按键的 `Action_Value`；子标签（Sub_Label）不参与输入，因此本函数不接收子标签，
 * 仅按 `Action_Value` 处理（Requirement 11.14）。
 *
 * 注意：Requirement 11.17“无活动输入连接时丢弃该次触发并保留 shift/caps 状态”属于宿主层职责——
 * 本纯函数只负责返回 [EmitResult]，是否真正写入输入连接、以及无连接时的丢弃，由调用方决定。
 */
object KeyEmissionDecider {

    /**
     * 依据 [action]（按键 `Action_Value`）与大小写变换 [transform] 决定发射结果。
     *
     * 规则：
     * - "Shift" -> [EmitResult.NoOp]：修饰键，不输出（Requirement 12.8 由调用方在发射字母后清除 shift）。
     * - "123" / "中/英" -> [EmitResult.NoOp]：占位键，不输出、不切换（Requirements 11.6-11.9）。
     * - "Del" -> [EmitResult.ControlAction]("Del")（Requirement 11.3）。
     * - "Enter" -> [EmitResult.ControlAction]("Enter")（Requirement 11.4）。
     * - " "（空格）-> [EmitResult.Commit](" ")（Requirement 11.5）。
     * - "，"（逗号）-> [EmitResult.Commit]("，")（Requirement 11.2）。
     * - 其他字面字符 -> [EmitResult.Commit]([transform]([action]))：先经 shift/caps 变换再提交
     *   （Requirements 11.1、11.13、12.8）。
     *
     * @param action 被触发按键的 `Action_Value`。
     * @param transform 大小写变换函数（由 ShiftState 提供）；仅作用于字面字符，
     *                  对空格、逗号、控制键与占位键不调用，以保持松耦合。
     */
    fun decideEmission(action: String, transform: (String) -> String): EmitResult = when (action) {
        // 修饰键与占位键：不输出
        "Shift", "123", "中/英" -> EmitResult.NoOp
        // 控制动作
        "Del" -> EmitResult.ControlAction("Del")
        "Enter" -> EmitResult.ControlAction("Enter")
        // 固定字面：空格与逗号直接提交，不经大小写变换
        " " -> EmitResult.Commit(" ")
        "，" -> EmitResult.Commit("，")
        // 其余字面字符：经 shift/caps 变换后提交
        else -> EmitResult.Commit(transform(action))
    }

    /**
     * 便捷重载：当调用方不需要大小写变换（例如已预处理好文本）时使用恒等变换。
     */
    fun decideEmission(action: String): EmitResult = decideEmission(action) { it }
}
