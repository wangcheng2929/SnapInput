package com.study.snapinput.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.study.snapinput.data.dao.WordDao
import com.study.snapinput.data.model.WordEntity

@Database(entities = [WordEntity::class], version = 1, exportSchema = false)
abstract class WordDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
}
