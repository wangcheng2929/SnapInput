package com.study.snapinput.core.ime

import android.content.Context
import android.content.res.Configuration
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 输入法支持的语言。
 */
data class Language(val code: String, val displayName: String)

/**
 * 管理输入法当前语言及可切换的语言列表。
 */
@Singleton
class LanguageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun getCurrentLanguage(): String =
        context.resources.configuration.locales[0].language

    fun getSupportedLanguages(): List<Language> = listOf(
        Language("zh_CN", "中文"),
        Language("en_US", "English"),
        Language("ja_JP", "日本語"),
        Language("ko_KR", "한국어")
    )

    fun applyLanguage(languageCode: String) {
        val locale = Locale(languageCode.substringBefore("_"))
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }
}
