package com.m.s.micosaver.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.m.s.micosaver.db.dao.RecommendDao
import com.m.s.micosaver.db.info.RecommendBean
import com.m.s.micosaver.ms

@Database(
    entities = [RecommendBean::class],
    version = 1,
    exportSchema = false
)
abstract class RecommendDataBase : RoomDatabase() {

    companion object {
        val database by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            Room.databaseBuilder(
                ms,
                RecommendDataBase::class.java,
                "ms_recommend.db"
            ).allowMainThreadQueries().build()
        }
    }

    abstract fun recommendDao(): RecommendDao

}