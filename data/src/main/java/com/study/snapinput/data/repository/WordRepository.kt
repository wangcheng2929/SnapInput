package com.study.snapinput.data.repository

import com.study.snapinput.data.dao.WordDao
import com.study.snapinput.data.model.Word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WordRepository(private val wordDao: WordDao) {
    suspend fun addWord(word: String, language: String = "zh_CN") {
        withContext(Dispatchers.IO) {
            val existingWord = wordDao.getWord(word)
            if (existingWord != null) {
                // 更新频率
                val updatedWord = existingWord.copy(frequency = existingWord.frequency + 1)
                wordDao.update(updatedWord)
            } else {
                // 插入新单词
                val newWord = Word(word = word, language = language)
                wordDao.insert(newWord)
            }
        }
    }
    
    suspend fun getWordsByPrefix(prefix: String, language: String = "zh_CN", limit: Int = 5): List<String> {
        return withContext(Dispatchers.IO) {
            wordDao.getWordsByPrefix(prefix, limit)
                .map { it.word }
        }
    }
    
    suspend fun getTopWords(language: String = "zh_CN", limit: Int = 10): List<String> {
        return withContext(Dispatchers.IO) {
            wordDao.getTopWords(language, limit)
                .map { it.word }
        }
    }
    
    suspend fun deleteWord(word: String) {
        withContext(Dispatchers.IO) {
            val existingWord = wordDao.getWord(word)
            if (existingWord != null) {
                wordDao.deleteWord(existingWord.id)
            }
        }
    }
}