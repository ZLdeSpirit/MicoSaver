package com.m.s.micosaver.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.m.s.micosaver.db.info.SavingVideoInfo

@Dao
interface SavingVideoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(videoInfo: SavingVideoInfo)

    @Query("SELECT * FROM ms_saving_video_info WHERE ms_is_finish==0 ORDER BY ms_id DESC")
    fun getNeedDownloadList(): List<SavingVideoInfo>

    @Query("SELECT * FROM ms_saving_video_info WHERE ms_is_finish==1 LIMIT 1")
    fun getFinishInfo(): SavingVideoInfo?

    @Delete
    fun delete(info: SavingVideoInfo)

}