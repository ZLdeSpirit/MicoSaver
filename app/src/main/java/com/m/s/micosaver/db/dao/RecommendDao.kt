package com.m.s.micosaver.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.m.s.micosaver.db.info.RecommendBean

@Dao
interface RecommendDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRecommend(recommend: RecommendBean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRecommend(recommendList: List<RecommendBean>)

    @Query("SELECT * FROM ${RecommendBean.TABLE_NAME}")
    fun queryRecommend(): List<RecommendBean>

    @Query("DELETE FROM ${RecommendBean.TABLE_NAME}")
    fun clearAllRecommends()

    @Transaction
    suspend fun replaceAllRecommends(recommends: List<RecommendBean>) {
        clearAllRecommends()
        recommends.forEach { insertRecommend(it) }
    }
}