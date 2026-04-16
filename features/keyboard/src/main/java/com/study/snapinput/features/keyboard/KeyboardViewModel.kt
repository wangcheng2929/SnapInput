package com.study.snapinput.features.keyboard

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class KeyboardViewModel : ViewModel() {
    private val _isCapsLockEnabled = MutableStateFlow(false)
    val isCapsLockEnabled: StateFlow<Boolean> = _isCapsLockEnabled
    
    private val _isShiftEnabled = MutableStateFlow(false)
    val isShiftEnabled: StateFlow<Boolean> = _isShiftEnabled
    
    private val _currentLayout = MutableStateFlow(KeyboardLayoutType.QWERTY)
    val currentLayout: StateFlow<KeyboardLayoutType> = _currentLayout
    
    fun toggleCapsLock() {
        _isCapsLockEnabled.value = !_isCapsLockEnabled.value
    }
    
    fun toggleShift() {
        _isShiftEnabled.value = !_isShiftEnabled.value
    }
    
    fun resetShift() {
        _isShiftEnabled.value = false
    }
    
    fun switchLayout(layoutType: KeyboardLayoutType) {
        _currentLayout.value = layoutType
    }
}

enum class KeyboardLayoutType {
    QWERTY,
    NUMERIC,
    SYMBOLS
}