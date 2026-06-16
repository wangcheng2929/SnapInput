package com.study.snapinput.data.di

import android.content.Context
import androidx.room.Room
import com.study.snapinput.data.dao.WordDao
import com.study.snapinput.data.database.WordDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideWordDatabase(@ApplicationContext context: Context): WordDatabase =
        Room.databaseBuilder(context, WordDatabase::class.java, "word_database").build()

    @Provides
    fun provideWordDao(database: WordDatabase): WordDao = database.wordDao()
}
