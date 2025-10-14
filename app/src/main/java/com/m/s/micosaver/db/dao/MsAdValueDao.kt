package com.m.s.micosaver.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.m.s.micosaver.db.info.MsAdValue

@Dao
interface MsAdValueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(adValue: MsAdValue)

    @Query("SELECT * FROM ms_ad_value")
    fun getAdValueList(): List<MsAdValue>

    @Delete
    fun delete(adValue: MsAdValue)

}