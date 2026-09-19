package com.m.s.micosaver.db.info

import android.os.Parcel
import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = RecommendBean.TABLE_NAME)
class RecommendBean(

    @ColumnInfo("video_id")
    val videoId: String,

    @ColumnInfo("url")
    val url: String,

    @ColumnInfo("dl_url")
    val downloadUrl: String,

    @ColumnInfo("cover")
    val cover: String,

    @ColumnInfo("avatar")
    val avatar: String,

    @ColumnInfo("desc")
    val desc: String,

    @ColumnInfo("author_name")
    val authorName: String,

    @ColumnInfo("like_count")
    val likeCount: String,

    @ColumnInfo("topics")
    val topics: String,

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo("auto_id")
    val id: Long = 0,
): Parcelable{

    @ColumnInfo("f_vid_duration")
    var videoDuration: Long = 0

    @ColumnInfo("videos")
    var videos: String = ""//JSONArray

    @ColumnInfo("audio")
    var audio: String = ""//音频地址

    @ColumnInfo("field1")
    var field1: String = ""

    @ColumnInfo("field2")
    var field2: String = ""

    @ColumnInfo("field3")
    var field3: String = ""

//    @ColumnInfo("field4")
//    var field4: Long = 0
//
//    @ColumnInfo("field5")
//    var field5: String = ""
//
//    @ColumnInfo("field6")
//    var field6: String = ""

    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readLong()
    ) {
        videoDuration = parcel.readLong()
        videos = parcel.readString() ?: ""
        audio = parcel.readString() ?: ""
        field1 = parcel.readString() ?: ""
        field2 = parcel.readString() ?: ""
        field3 = parcel.readString() ?: ""
//        field4 = parcel.readLong()
//        field5 = parcel.readString() ?: ""
//        field6 = parcel.readString() ?: ""
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(videoId)
        parcel.writeString(url)
        parcel.writeString(downloadUrl)
        parcel.writeString(cover)
        parcel.writeString(avatar)
        parcel.writeString(desc)
        parcel.writeString(authorName)
        parcel.writeString(likeCount)
        parcel.writeString(topics)
        parcel.writeLong(id)
        parcel.writeLong(videoDuration)
        parcel.writeString(videos)
        parcel.writeString(audio)
        parcel.writeString(field1)
        parcel.writeString(field2)
        parcel.writeString(field3)
//        parcel.writeLong(field4)
//        parcel.writeString(field5)
//        parcel.writeString(field6)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<RecommendBean> {
        const val TABLE_NAME = "recommend"

        override fun createFromParcel(parcel: Parcel): RecommendBean {
            return RecommendBean(parcel)
        }

        override fun newArray(size: Int): Array<RecommendBean?> {
            return arrayOfNulls(size)
        }
    }
}