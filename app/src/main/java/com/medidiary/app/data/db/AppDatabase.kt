package com.medidiary.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DiarySession::class, DiaryRecord::class, DgdaMedicine::class, DgdaAlias::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao
    abstract fun dgdaDao(): DgdaDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "medidiary.db").build()
    }
}
