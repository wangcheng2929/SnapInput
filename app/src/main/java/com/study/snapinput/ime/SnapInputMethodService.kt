package com.study.snapinput.ime

import android.text.TextUtils
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.study.snapinput.core.ime.LanguageManager
import com.study.snapinput.core.usecase.GetPredictionsUseCase
import com.study.snapinput.core.usecase.LearnWordUseCase
import com.study.snapinput.feature.keyboard.KeyboardLayout
import com.study.snapinput.feature.prediction.PredictionBar
import com.study.snapinput.ui.theme.SnapInputTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import android.inputmethodservice.InputMethodService
import javax.inject.Inject

/**
 * SnapInput 输入法服务（编排层）。
 *
 * - 通过 Hilt 注入业务 UseCase（[GetPredictionsUseCase] / [LearnWordUseCase]）与 [LanguageManager]
 * - 用 [ComposeView] 挂载 feature 层的无状态键盘 / 候选词组件
 * - 负责把按键动作落到 [android.view.inputmethod.InputConnection]（提交文本、删除等）
 *
 * 因为 IME Service 不是 Activity，需要自行充当 Compose 所需的
 * LifecycleOwner / ViewModelStoreOwner / SavedStateRegistryOwner。
 */
@AndroidEntryPoint
class SnapInputMethodService :
    InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    @Inject lateinit var getPredictions: GetPredictionsUseCase
    @Inject lateinit var learnWord: LearnWordUseCase
    @Inject lateinit var languageManager: LanguageManager

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** 当前正在输入的词缓冲，用于前缀联想与学习。 */
    private val wordBuffer = StringBuilder()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onCreateInputView(): View {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        // IME 会把输入视图包进自己的父容器（parentPanel 等），Compose 创建 recomposer 时
        // 是从“窗口根视图”向上查找 owner 的。因此必须把 owner 设到窗口 decorView（根视图的祖先），
        // 仅设在 ComposeView 自身会导致 “ViewTreeLifecycleOwner not found”。
        window?.window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this)
            decor.setViewTreeViewModelStoreOwner(this)
            decor.setViewTreeSavedStateRegistryOwner(this)
        }

        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@SnapInputMethodService)
            setViewTreeViewModelStoreOwner(this@SnapInputMethodService)
            setViewTreeSavedStateRegistryOwner(this@SnapInputMethodService)
            setContent { KeyboardContent() }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        wordBuffer.clear()
    }

    @Composable
    private fun KeyboardContent() {
        var predictions by remember { mutableStateOf<List<String>>(emptyList()) }
        val language = languageManager.getCurrentLanguage()

        SnapInputTheme {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE8E8E8))
                    .padding(4.dp)
            ) {
                PredictionBar(
                    predictions = predictions,
                    onPredictionSelected = { word ->
                        commitPrediction(word, language)
                        predictions = emptyList()
                    }
                )
                KeyboardLayout(
                    onKeyPressed = { key ->
                        handleKey(key, language) { predictions = it }
                    }
                )
            }
        }
    }

    private fun handleKey(
        key: String,
        language: String,
        onPredictions: (List<String>) -> Unit
    ) {
        val ic = currentInputConnection ?: return
        when (key) {
            "Del" -> {
                ic.deleteSurroundingText(1, 0)
                if (wordBuffer.isNotEmpty()) wordBuffer.deleteCharAt(wordBuffer.length - 1)
                refreshPredictions(language, onPredictions)
            }

            "Enter" -> {
                ic.commitText("\n", 1)
                finishWord(language, onPredictions)
            }

            " " -> {
                ic.commitText(" ", 1)
                finishWord(language, onPredictions)
            }

            "123", "Sym" -> {
                // TODO: 切换数字 / 符号布局
            }

            else -> {
                ic.commitText(key, 1)
                wordBuffer.append(key)
                refreshPredictions(language, onPredictions)
            }
        }
    }

    private fun finishWord(language: String, onPredictions: (List<String>) -> Unit) {
        val word = wordBuffer.toString()
        if (!TextUtils.isEmpty(word)) {
            serviceScope.launch { learnWord(word, language) }
        }
        wordBuffer.clear()
        onPredictions(emptyList())
    }

    private fun refreshPredictions(language: String, onPredictions: (List<String>) -> Unit) {
        val prefix = wordBuffer.toString()
        serviceScope.launch {
            onPredictions(getPredictions(prefix, language))
        }
    }

    private fun commitPrediction(word: String, language: String) {
        val ic = currentInputConnection ?: return
        // 用候选词替换当前已输入的前缀
        if (wordBuffer.isNotEmpty()) {
            ic.deleteSurroundingText(wordBuffer.length, 0)
        }
        ic.commitText(word, 1)
        serviceScope.launch { learnWord(word, language) }
        wordBuffer.clear()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        serviceScope.cancel()
        super.onDestroy()
    }
}
