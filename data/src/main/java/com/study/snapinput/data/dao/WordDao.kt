package com.study.snapinput.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.study.snapinput.data.model.Word

@Dao
interface WordDao {
    @Insert
    suspend fun insert(word: Word)
    
    @Update
    suspend fun update(word: Word)
    
    @Query("SELECT * FROM words WHERE word LIKE :prefix || '%' ORDER BY frequency DESC LIMIT :limit")
    suspend fun getWordsByPrefix(prefix: String, limit: Int): List<Word>
    
    @Query("SELECT * FROM words WHERE language = :language ORDER BY frequency DESC LIMIT :limit")
    suspend fun getTopWords(language: String, limit: Int): List<Word>
    
    @Query("SELECT * FROM words WHERE word = :word LIMIT 1")
    suspend fun getWord(word: String): Word?
    
    @Query("DELETE FROM words WHERE id = :id")
    suspend fun deleteWord(id: Int)
}