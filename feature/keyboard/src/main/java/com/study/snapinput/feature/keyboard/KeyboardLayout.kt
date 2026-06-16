package com.study.snapinput.feature.keyboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 无状态的 QWERTY 键盘布局，可被任意宿主（IME Service / Activity）复用。
 *
 * 大小写（Caps / Shift）属于纯 UI 局部状态，由组件内部 [remember] 管理；
 * 实际的字符/功能键通过 [onKeyPressed] 上抛给宿主处理（提交文本、删除等）。
 *
 * 上抛的 key：已处理好大小写的字母、" "(空格)、"Del"、"Enter"、"123"、"Sym"。
 */
@Composable
fun KeyboardLayout(
    onKeyPressed: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var capsLock by remember { mutableStateOf(false) }
    var shift by remember { mutableStateOf(false) }
    val upperCase = capsLock || shift

    fun emit(raw: String) {
        onKeyPressed(if (upperCase) raw.uppercase() else raw.lowercase())
        if (shift) shift = false
    }

    fun display(c: String) = if (upperCase) c.uppercase() else c.lowercase()

    Column(modifier = modifier) {
        Row {
            listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P").forEach {
                KeyButton(text = display(it), onClick = { emit(it) }, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(2.dp))
            }
        }
        Spacer(modifier = Modifier.height(2.dp))

        Row {
            KeyButton(
                text = if (capsLock) "CAPS" else "Caps",
                onClick = { capsLock = !capsLock },
                modifier = Modifier.weight(1.5f),
                isSpecialKey = true
            )
            Spacer(modifier = Modifier.width(2.dp))
            listOf("A", "S", "D", "F", "G", "H", "J", "K", "L").forEach {
                KeyButton(text = display(it), onClick = { emit(it) }, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(2.dp))
            }
        }
        Spacer(modifier = Modifier.height(2.dp))

        Row {
            KeyButton(
                text = "Shift",
                onClick = { shift = !shift },
                modifier = Modifier.weight(1.8f),
                isSpecialKey = true
            )
            Spacer(modifier = Modifier.width(2.dp))
            listOf("Z", "X", "C", "V", "B", "N", "M").forEach {
                KeyButton(text = display(it), onClick = { emit(it) }, modifier = Modifier.weight(1f))
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

        Row {
            KeyButton(text = "123", onClick = { onKeyPressed("123") }, modifier = Modifier.weight(1f), isSpecialKey = true)
            Spacer(modifier = Modifier.width(2.dp))
            KeyButton(text = "Sym", onClick = { onKeyPressed("Sym") }, modifier = Modifier.weight(1f), isSpecialKey = true)
            Spacer(modifier = Modifier.width(2.dp))
            KeyButton(text = " ", onClick = { onKeyPressed(" ") }, modifier = Modifier.weight(5f))
            Spacer(modifier = Modifier.width(2.dp))
            KeyButton(text = "Enter", onClick = { onKeyPressed("Enter") }, modifier = Modifier.weight(1.5f), isSpecialKey = true)
        }
    }
}
