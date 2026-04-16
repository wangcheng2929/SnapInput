package com.study.snapinput.features.keyboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun KeyboardLayout(
    onKeyPressed: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // 第一行：Q W E R T Y U I O P
        Row {
            listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P").forEach {
                KeyButton(
                    text = it,
                    onClick = { onKeyPressed(it) },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(2.dp))
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        
        // 第二行：A S D F G H J K L
        Row {
            KeyButton(
                text = "Caps",
                onClick = { onKeyPressed("Caps") },
                modifier = Modifier.weight(1.5f),
                isSpecialKey = true
            )
            Spacer(modifier = Modifier.width(2.dp))
            listOf("A", "S", "D", "F", "G", "H", "J", "K", "L").forEach {
                KeyButton(
                    text = it,
                    onClick = { onKeyPressed(it) },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(2.dp))
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        
        // 第三行：Shift Z X C V B N M
        Row {
            KeyButton(
                text = "Shift",
                onClick = { onKeyPressed("Shift") },
                modifier = Modifier.weight(1.8f),
                isSpecialKey = true
            )
            Spacer(modifier = Modifier.width(2.dp))
            listOf("Z", "X", "C", "V", "B", "N", "M").forEach {
                KeyButton(
                    text = it,
                    onClick = { onKeyPressed(it) },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(2.dp))
            }
            KeyButton(
                text = "Del",
                onClick = { onKeyPressed("Del") },
                modifier = Modifier.weight(1.8f),
                isSpecialKey = true
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        
        // 第四行：数字、符号、空格、回车
        Row {
            KeyButton(
                text = "123",
                onClick = { onKeyPressed("123") },
                modifier = Modifier.weight(1f),
                isSpecialKey = true
            )
            Spacer(modifier = Modifier.width(2.dp))
            KeyButton(
                text = "Sym",
                onClick = { onKeyPressed("Sym") },
                modifier = Modifier.weight(1f),
                isSpecialKey = true
            )
            Spacer(modifier = Modifier.width(2.dp))
            KeyButton(
                text = " ",
                onClick = { onKeyPressed(" ") },
                modifier = Modifier.weight(5f)
            )
            Spacer(modifier = Modifier.width(2.dp))
            KeyButton(
                text = "Enter",
                onClick = { onKeyPressed("Enter") },
                modifier = Modifier.weight(1.5f),
                isSpecialKey = true
            )
        }
    }
}