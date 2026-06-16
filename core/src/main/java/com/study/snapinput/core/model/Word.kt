package com.study.snapinput.core.model

/**
 * 领域层的词条模型，与具体存储实现（Room 等）解耦。
 *
 * @param text 词条文本
 * @param frequency 使用频率，用于候选词排序
 * @param language 所属语言，如 "zh_CN"
 */
data class Word(
    val text: String,
    val frequency: Int = 1,
    val language: String = "zh_CN"
)
