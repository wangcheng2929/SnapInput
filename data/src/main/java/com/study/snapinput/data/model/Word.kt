package com.study.snapinput.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "words")
data class Word(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val word: String,
    val frequency: Int = 1,
    val language: String = "zh_CN",
    val timestamp: Long = System.currentTimeMillis()
)