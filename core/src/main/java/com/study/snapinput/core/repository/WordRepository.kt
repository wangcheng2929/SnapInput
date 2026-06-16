package com.study.snapinput.core.repository

import com.study.snapinput.core.model.Word

/**
 * 词库仓库接口，由 data 层实现（依赖倒置）。
 * core 只定义业务需要的能力，不关心存储细节。
 */
interface WordRepository {

    /** 按前缀查询候选词，按词频降序返回。 */
    suspend fun getWordsByPrefix(prefix: String, language: String, limit: Int): List<Word>

    /** 获取某语言下的高频词。 */
    suspend fun getTopWords(language: String, limit: Int): List<Word>

    /** 学习一个词：已存在则词频 +1，否则新增。 */
    suspend fun learnWord(text: String, language: String)

    /** 删除一个词。 */
    suspend fun deleteWord(text: String, language: String)
}
