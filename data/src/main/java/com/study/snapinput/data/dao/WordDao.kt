package com.study.snapinput.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.study.snapinput.data.model.WordEntity

@Dao
interface WordDao {

    @Insert
    suspend fun insert(word: WordEntity)

    @Update
    suspend fun update(word: WordEntity)

    @Query(
        "SELECT * FROM words WHERE word LIKE :prefix || '%' AND language = :language " +
            "ORDER BY frequency DESC LIMIT :limit"
    )
    suspend fun getWordsByPrefix(prefix: String, language: String, limit: Int): List<WordEntity>

    @Query("SELECT * FROM words WHERE language = :language ORDER BY frequency DESC LIMIT :limit")
    suspend fun getTopWords(language: String, limit: Int): List<WordEntity>

    @Query("SELECT * FROM words WHERE word = :word AND language = :language LIMIT 1")
    suspend fun getWord(word: String, language: String): WordEntity?

    @Query("DELETE FROM words WHERE word = :word AND language = :language")
    suspend fun deleteWord(word: String, language: String)
}
