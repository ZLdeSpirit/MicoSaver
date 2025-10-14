package com.m.s.micosaver.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.m.s.micosaver.db.dao.MsAdValueDao
import com.m.s.micosaver.db.dao.SavedVideoDao
import com.m.s.micosaver.db.dao.SavingVideoDao
import com.m.s.micosaver.db.info.MsAdValue
import com.m.s.micosaver.db.info.SavedVideoInfo
import com.m.s.micosaver.db.info.SavingVideoInfo
import com.m.s.micosaver.ms

@Database(
    entities = [MsAdValue::class, SavingVideoInfo::class, SavedVideoInfo::class],
    version = 1,
    exportSchema = false
)
abstract class MsDataBase : RoomDatabase() {

    companion object {
        val database by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            Room.databaseBuilder(
                ms,
                MsDataBase::class.java,
                "ms_database.db"
            ).allowMainThreadQueries().build()
        }
    }

    abstract fun proAdValueDao(): MsAdValueDao

    abstract fun savingVideoDao(): SavingVideoDao

    abstract fun savedVideoDao(): SavedVideoDao

}