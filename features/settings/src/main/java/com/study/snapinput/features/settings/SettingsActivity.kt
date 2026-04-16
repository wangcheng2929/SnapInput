package com.study.snapinput.features.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.study.snapinput.ui.theme.SnapInputTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SnapInputTheme {
                SettingsScreen()
            }
        }
    }
}