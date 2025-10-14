package com.m.s.micosaver.db.info

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ms_ad_value")
class MsAdValue(
    @ColumnInfo(name = "ms_value")
    val value: Long,
    @ColumnInfo(name = "ms_code")
    val code: String,
    @ColumnInfo(name = "ms_ad_id")
    val adId: String,
    @ColumnInfo(name = "ms_type")
    val type: Int,
    @ColumnInfo(name = "ms_source_name")
    val sourceName: String?,
    @ColumnInfo(name = "ms_id")
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0
)