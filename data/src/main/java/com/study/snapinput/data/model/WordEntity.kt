package com.study.snapinput.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.study.snapinput.core.model.Word

/**
 * Room 持久化实体。仅存在于 data 层，不外泄到 core / feature。
 */
@Entity(
    tableName = "words",
    indices = [Index(value = ["word", "language"], unique = true)]
)
data class WordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val word: String,
    val frequency: Int = 1,
    val language: String = "zh_CN",
    val timestamp: Long = System.currentTimeMillis()
)

/** 实体 -> 领域模型 */
fun WordEntity.toDomain(): Word =
    Word(text = word, frequency = frequency, language = language)
