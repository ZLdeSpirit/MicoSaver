package com.m.s.micosaver.db.info

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ms_saved_video_info")
class SavedVideoInfo(
    @ColumnInfo("ms_video_path")
    @PrimaryKey(autoGenerate = false)
    val videoPath: String,
    @ColumnInfo("ms_video_length")
    val videoLength: Long,
    @ColumnInfo("ms_video_desc")
    val videoDesc: String,
    @ColumnInfo("ms_aut_nm")
    var authorName: String,
    @ColumnInfo("ms_is_played")
    var isPlayed: Boolean = false,
    @ColumnInfo("ms_is_liked")
    var isLiked: Boolean = false,
    @ColumnInfo("ms_downl_time")
    val downloadTime: Long = System.currentTimeMillis()
)