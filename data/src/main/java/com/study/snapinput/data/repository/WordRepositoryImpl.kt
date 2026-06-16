package com.study.snapinput.data.repository

import com.study.snapinput.core.model.Word
import com.study.snapinput.core.repository.WordRepository
import com.study.snapinput.data.dao.WordDao
import com.study.snapinput.data.model.WordEntity
import com.study.snapinput.data.model.toDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * [WordRepository] 的 Room 实现。
 */
class WordRepositoryImpl @Inject constructor(
    private val wordDao: WordDao
) : WordRepository {

    override suspend fun getWordsByPrefix(
        prefix: String,
        language: String,
        limit: Int
    ): List<Word> = withContext(Dispatchers.IO) {
        wordDao.getWordsByPrefix(prefix, language, limit).map { it.toDomain() }
    }

    override suspend fun getTopWords(language: String, limit: Int): List<Word> =
        withContext(Dispatchers.IO) {
            wordDao.getTopWords(language, limit).map { it.toDomain() }
        }

    override suspend fun learnWord(text: String, language: String) {
        withContext(Dispatchers.IO) {
            val existing = wordDao.getWord(text, language)
            if (existing != null) {
                wordDao.update(existing.copy(frequency = existing.frequency + 1))
            } else {
                wordDao.insert(WordEntity(word = text, language = language))
            }
        }
    }

    override suspend fun deleteWord(text: String, language: String) {
        withContext(Dispatchers.IO) {
            wordDao.deleteWord(text, language)
        }
    }
}
