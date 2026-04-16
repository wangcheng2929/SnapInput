package com.study.snapinput.features.prediction

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PredictionResults(
    predictions: List<String>,
    onPredictionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth()
    ) {
        predictions.forEachIndexed { index, prediction ->
            Text(
                text = prediction,
                fontSize = 16.sp,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        onPredictionSelected(prediction)
                    }
            )
            if (index < predictions.size - 1) {
                Spacer(modifier = Modifier.width(16.dp))
            }
        }
    }
}