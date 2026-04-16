package com.study.snapinput.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.study.snapinput.data.dao.WordDao
import com.study.snapinput.data.model.Word

@Database(entities = [Word::class], version = 1)
abstract class WordDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
}