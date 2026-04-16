package com.study.snapinput.core

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

class LanguageManager(private val context: Context) {
    fun getCurrentLanguage(): String {
        val locale = context.resources.configuration.locales[0]
        return locale.language
    }
    
    fun getSupportedLanguages(): List<Language> {
        return listOf(
            Language("zh_CN", "中文"),
            Language("en_US", "English"),
            Language("ja_JP", "日本語"),
            Language("ko_KR", "한국어")
        )
    }
    
    fun setLanguage(languageCode: String) {
        val locale = Locale(languageCode.split("_")[0])
        Locale.setDefault(locale)
        
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }
}

data class Language(val code: String, val name: String)