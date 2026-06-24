package com.study.snapinput.feature.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 顶部区域（Top_Region）高度比例：由 [TopRegion] 组件拥有的内置常量。
 *
 * 该常量**不**是 Keyboard_Config 字段、不在 JSON 配置中定义、不由配置驱动（Requirement 10.2 / 9.2）。
 * Top_Region 高度 = Screen_Height H × [TOP_REGION_HEIGHT_RATIO]。
 */
const val TOP_REGION_HEIGHT_RATIO = 0.065f

/**
 * 顶部区域（Top_Region）组件：渲染在 Keyboard_Region 正上方的独立组件。
 *
 * 其存在、布局与内容**不**受 Keyboard_Config 控制（Requirement 10.1）；高度由宿主传入的
 * 已计算像素值 [heightPx]（= H × [TOP_REGION_HEIGHT_RATIO]）决定（Requirement 9.2 / 10.2）。
 *
 * 两种互斥显示模式由 [selectTopRegionMode] 按 Word_Buffer 是否为空选择：
 * - Word_Buffer 为空 → 显示 Toolbar：居左 Apps_Entry（占位）、居右 Collapse_Keyboard_Button（Requirement 10.6）。
 * - Word_Buffer 非空 → 内嵌候选词栏（Prediction_Bar），呈现候选词预测。
 *
 * @param wordBufferEmpty Word_Buffer 是否为空。
 * @param predictions 候选词列表（仅在显示候选词栏时使用）。
 * @param onPredictionSelected 候选词被点击时的回调。
 * @param onCollapseKeyboard Collapse_Keyboard_Button 被按下时的回调（宿主据此收起输入法）。
 * @param heightPx 顶部区域像素高度，由宿主按 H × [TOP_REGION_HEIGHT_RATIO] 计算后传入。
 * @param modifier 外部修饰符。
 */
@Composable
fun TopRegion(
    wordBufferEmpty: Boolean,
    predictions: List<String>,
    onPredictionSelected: (String) -> Unit,
    onCollapseKeyboard: () -> Unit,
    heightPx: Float,
    modifier: Modifier = Modifier
) {
    // 像素高度 → dp（不跟随系统字号缩放，仅做密度换算）
    val heightDp = with(LocalDensity.current) { heightPx.toDp() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp)
    ) {
        when (selectTopRegionMode(wordBufferEmpty)) {
            // Word_Buffer 为空 → 工具栏（Requirement 10.3 / 10.6）
            TopRegionMode.Toolbar -> Toolbar(onCollapseKeyboard = onCollapseKeyboard)
            // Word_Buffer 非空 → 候选词栏（Requirement 10.4）
            TopRegionMode.PredictionBar -> PredictionRow(
                predictions = predictions,
                onPredictionSelected = onPredictionSelected
            )
        }
    }
}

/**
 * 工具栏（Toolbar）：本轮恰好包含两个元素——居左的 Apps_Entry 与居右的 Collapse_Keyboard_Button
 * （Requirement 10.6）。
 */
@Composable
private fun Toolbar(onCollapseKeyboard: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 居左：Apps_Entry（田字格图标，本轮为占位——仅呈现按下态视觉，不执行任何功能）
        ToolbarIcon(
            symbol = "⊞",
            onClick = null
        )
        // 居右：Collapse_Keyboard_Button（"∨" chevron 图标，按下时收起输入法）
        ToolbarIcon(
            symbol = "∨",
            onClick = onCollapseKeyboard
        )
    }
}

/**
 * 工具栏图标元素。
 *
 * - [onClick] 为 null 时（如 Apps_Entry 占位）：仍响应按下态视觉，但按下不执行任何功能（Requirement 10.7 / 10.8）。
 * - [onClick] 非 null 时（如 Collapse_Keyboard_Button）：呈现按下态视觉并在按下时触发回调（Requirement 10.9 / 10.10）。
 */
@Composable
private fun ToolbarIcon(
    symbol: String,
    onClick: (() -> Unit)?
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // 按下态视觉：按下时叠加浅色高亮背景
    val background = if (pressed) Color(0x33000000) else Color.Transparent

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                // 占位元素无功能：点击不做任何事，仅保留按下态视觉
                onClick = { onClick?.invoke() }
            )
            .background(background)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = symbol, fontSize = 20.sp)
    }
}

/**
 * 内嵌候选词栏（Prediction_Bar）：横向平分呈现候选词，点击回调 [onPredictionSelected]。
 *
 * 由于 feature:keyboard 未依赖 feature:prediction，此处实现一个自包含的最小候选词行，
 * 避免引入新的模块依赖（Requirement 10.4）。
 */
@Composable
private fun PredictionRow(
    predictions: List<String>,
    onPredictionSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        predictions.forEach { prediction ->
            Text(
                text = prediction,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onPredictionSelected(prediction) }
                    .padding(vertical = 12.dp)
            )
        }
    }
}
