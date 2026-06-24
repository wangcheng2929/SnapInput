package com.study.snapinput.ime

import android.text.TextUtils
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
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
import com.study.snapinput.core.config.ActiveConfig
import com.study.snapinput.core.config.AssetConfigLoader
import com.study.snapinput.core.config.DefaultConfig
import com.study.snapinput.core.config.KeyboardConfigProvider
import com.study.snapinput.core.config.layout.shrinkRegionHeights
import com.study.snapinput.core.ime.LanguageManager
import com.study.snapinput.core.usecase.GetPredictionsUseCase
import com.study.snapinput.core.usecase.LearnWordUseCase
import com.study.snapinput.feature.keyboard.EmitResult
import com.study.snapinput.feature.keyboard.KeyEmissionDecider
import com.study.snapinput.feature.keyboard.KeyboardRenderer
import com.study.snapinput.feature.keyboard.ShiftState
import com.study.snapinput.feature.keyboard.TOP_REGION_HEIGHT_RATIO
import com.study.snapinput.feature.keyboard.TopRegion
import com.study.snapinput.ui.theme.SnapInputTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    /** Shift / Caps Lock 状态机：决定字母按大小写发射（Requirement 11.1、11.10-11.13）。 */
    private val shiftState = ShiftState()

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
        // Word_Buffer 是否为空的 Compose 状态镜像：StringBuilder 的原地变更不会触发重组，
        // 因此用该状态驱动 TopRegion 在 Toolbar 与 PredictionBar 间切换（Requirement 10.5）。
        var wordBufferEmpty by remember { mutableStateOf(wordBuffer.isEmpty()) }
        val language = languageManager.getCurrentLanguage()

        // 配置加载（Requirement 4.4）：初始用内置默认（回退）配置先行渲染，
        // 同时在 IO 线程经 KeyboardConfigProvider.load() 得到生效配置后替换。
        // produceState 的协程默认运行在主线程，故内部用 withContext(IO) 执行实际加载。
        val activeConfig by produceState(
            initialValue = ActiveConfig(config = DefaultConfig.config, usingFallback = true)
        ) {
            value = withContext(Dispatchers.IO) {
                KeyboardConfigProvider(AssetConfigLoader(assets)).load()
            }
        }
        val config = activeConfig.config

        // 区域高度像素计算（Requirement 9.1 / 9.2 / 9.5）：
        // Top_Region 高度 = H × TOP_REGION_HEIGHT_RATIO；Keyboard_Region 高度 = H × keyboardRegionHeightRatio。
        val metrics = resources.displayMetrics
        val screenHeightPx = metrics.heightPixels.toFloat()
        val topRegionRawPx = screenHeightPx * TOP_REGION_HEIGHT_RATIO
        val keyboardRegionRawPx = screenHeightPx * config.keyboardRegionHeightRatio
        // 可用显示高度上限：输入法视图通常占屏幕下半部分，以屏幕高度一半作为合理界。
        val availableHeightPx = screenHeightPx * 0.5f
        // 两区域之和超过可用高度时按同一系数收缩（Requirement 9.5）。
        val regionHeights = shrinkRegionHeights(topRegionRawPx, keyboardRegionRawPx, availableHeightPx)
        val keyboardRegionDp = with(LocalDensity.current) { regionHeights.keyboardRegionPx.toDp() }

        SnapInputTheme {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE8E8E8))
            ) {
                // 顶部区域：Word_Buffer 空显示工具栏、非空内嵌候选词栏（Requirement 10.1）。
                TopRegion(
                    wordBufferEmpty = wordBufferEmpty,
                    predictions = predictions,
                    onPredictionSelected = { word ->
                        commitPrediction(word, language)
                        predictions = emptyList()
                        // 候选词提交后词缓冲清空，回到 Toolbar 模式。
                        wordBufferEmpty = true
                    },
                    onCollapseKeyboard = { requestHideSelf(0) },
                    heightPx = regionHeights.topRegionPx
                )
                // 键盘区：填满宽度（W = 屏幕宽度），高度取收缩后的 Keyboard_Region 高度。
                // onAction 经发射决策（KeyEmissionDecider）落到输入连接（Requirement 10.5、11.1-11.9、11.17）。
                KeyboardRenderer(
                    config = config,
                    usingFallback = activeConfig.usingFallback,
                    onAction = { key ->
                        handleKey(
                            key = key,
                            language = language,
                            onPredictions = { predictions = it },
                            setWordBufferEmpty = { wordBufferEmpty = it }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(keyboardRegionDp)
                )
            }
        }
    }

    private fun handleKey(
        key: String,
        language: String,
        onPredictions: (List<String>) -> Unit,
        setWordBufferEmpty: (Boolean) -> Unit
    ) {
        // Requirement 11.17：无活动输入连接时丢弃该次按键，且不改变 shift/caps 状态。
        val ic = currentInputConnection ?: return

        // Shift 为修饰键：仅更新 shift/caps 状态机，不产生任何输出（Requirement 11.10-11.13）。
        if (key == "Shift") {
            shiftState.onShiftTap(System.currentTimeMillis())
            return
        }

        // 经发射决策映射动作；字面字母经 shift/caps 变换（Requirement 11.1）。
        when (val result = KeyEmissionDecider.decideEmission(key, transform = shiftState::transformLetter)) {
            // 占位键（123 / 中/英）等：不输出、不切换布局或语言（Requirement 11.6-11.9）。
            EmitResult.NoOp -> Unit

            is EmitResult.ControlAction -> when (result.name) {
                // Del：删除前一个字符并同步词缓冲（Requirement 11.3）。
                "Del" -> {
                    ic.deleteSurroundingText(1, 0)
                    if (wordBuffer.isNotEmpty()) wordBuffer.deleteCharAt(wordBuffer.length - 1)
                    setWordBufferEmpty(wordBuffer.isEmpty())
                    refreshPredictions(language, onPredictions)
                }
                // Enter：提交换行并结束当前词（Requirement 11.4）。
                "Enter" -> {
                    ic.commitText("\n", 1)
                    finishWord(language, onPredictions)
                    setWordBufferEmpty(true)
                }
            }

            is EmitResult.Commit -> {
                val text = result.text
                if (text == " ") {
                    // 空格：提交并结束当前词（Requirement 11.5）。
                    ic.commitText(" ", 1)
                    finishWord(language, onPredictions)
                    setWordBufferEmpty(true)
                } else {
                    // 字面字符（含逗号 "，"）：提交并加入词缓冲（Requirement 11.1、11.2）。
                    ic.commitText(text, 1)
                    wordBuffer.append(text)
                    // 发射字面字母后清除一次性 Shift（ShiftOnce → None，Requirement 12.8）。
                    shiftState.afterLetterEmitted()
                    setWordBufferEmpty(wordBuffer.isEmpty())
                    refreshPredictions(language, onPredictions)
                }
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
