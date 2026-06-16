package com.study.snapinput.core.usecase

import com.study.snapinput.core.repository.WordRepository
import javax.inject.Inject

/**
 * 学习用户输入的词，用于提升后续候选词的命中率。
 */
class LearnWordUseCase @Inject constructor(
    private val wordRepository: WordRepository
) {
    suspend operator fun invoke(text: String, language: String = "zh_CN") {
        if (text.isBlank()) return
        wordRepository.learnWord(text.trim(), language)
    }
}
