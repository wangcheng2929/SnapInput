package com.study.snapinput.core.usecase

import com.study.snapinput.core.repository.WordRepository
import javax.inject.Inject

/**
 * 根据已输入前缀获取候选词列表。
 * 统一从词库读取，避免 UI 层直接接触数据细节。
 */
class GetPredictionsUseCase @Inject constructor(
    private val wordRepository: WordRepository
) {
    suspend operator fun invoke(
        prefix: String,
        language: String = "zh_CN",
        limit: Int = 5
    ): List<String> {
        if (prefix.isBlank()) return emptyList()
        return wordRepository.getWordsByPrefix(prefix, language, limit)
            .map { it.text }
    }
}
