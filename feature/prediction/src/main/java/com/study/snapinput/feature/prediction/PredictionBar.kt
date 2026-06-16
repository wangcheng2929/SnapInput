package com.study.snapinput.feature.prediction

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 横向候选词条。点击候选词回调 [onPredictionSelected]。
 */
@Composable
fun PredictionBar(
    predictions: List<String>,
    onPredictionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth()) {
        predictions.forEach { prediction ->
            Text(
                text = prediction,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onPredictionSelected(prediction) }
                    .padding(vertical = 12.dp)
            )
        }
    }
}
