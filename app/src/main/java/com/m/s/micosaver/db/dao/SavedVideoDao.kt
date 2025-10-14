package com.m.s.micosaver.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.m.s.micosaver.db.info.SavedVideoInfo

@Dao
interface SavedVideoDao {

    @Query("SELECT * FROM ms_saved_video_info ORDER BY ms_downl_time DESC")
    fun getAllVideos(): List<SavedVideoInfo>

    @Query("SELECT * FROM ms_saved_video_info WHERE ms_is_liked==1 ORDER BY ms_downl_time DESC")
    fun getLikedVideos(): List<SavedVideoInfo>

    @Query("SELECT COUNT(*) FROM ms_saved_video_info WHERE ms_is_played==0")
    fun noPlayCount(): Int

    @Query("SELECT * FROM ms_saved_video_info WHERE ms_video_path=:videoPath")
    fun getSavedVideoInfo(videoPath: String): SavedVideoInfo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(savedVideoInfo: SavedVideoInfo)

    @Delete
    fun delete(savedVideoInfo: SavedVideoInfo)

    @Delete
    fun delete(videoList: List<SavedVideoInfo>)

}