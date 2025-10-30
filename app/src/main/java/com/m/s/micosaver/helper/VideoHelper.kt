package com.m.s.micosaver.helper

import android.content.Context
import android.content.Intent
import android.util.Patterns
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.m.s.micosaver.broadcast.BroadcastHelper
import com.m.s.micosaver.channel.AppChannelHelper
import com.m.s.micosaver.db.MsDataBase
import com.m.s.micosaver.db.info.SavedVideoInfo
import com.m.s.micosaver.db.info.SavingVideoInfo
import com.m.s.micosaver.ex.scope
import kotlinx.coroutines.launch
import java.io.File

object VideoHelper {
    val savingVideoInfoList = mutableListOf<SavingVideoInfo>()

    fun initVideo() {
        scope.launch {
            val list = MsDataBase.database.savingVideoDao().getNeedDownloadList()
            if (list.isEmpty()) return@launch
            savingVideoInfoList.addAll(list)
            BroadcastHelper.sendBroadcast(BroadcastHelper.ACTION_SAVING_VIDEO_CHANGE)
        }
    }

    fun savingVideoInfo(savingVideoInfo: SavingVideoInfo) {
        for (info in savingVideoInfoList) {
            if (info.id == savingVideoInfo.id) {
                return
            }
        }
        savingVideoInfoList.add(0, savingVideoInfo)
        BroadcastHelper.sendBroadcast(BroadcastHelper.ACTION_SAVING_VIDEO_CHANGE)
    }

    fun removeSavingVideoInfo(savingVideoInfo: SavingVideoInfo) {
        savingVideoInfoList.remove(savingVideoInfo)
        BroadcastHelper.sendBroadcast(BroadcastHelper.ACTION_SAVING_VIDEO_CHANGE)
    }

    fun addSavedVideo(videoPath: String, length: Long, videoDesc: String, author: String) {
        scope.launch {
            MsDataBase.database.savedVideoDao()
                .insert(SavedVideoInfo(videoPath, length, videoDesc, author))
            BroadcastHelper.sendBroadcast(BroadcastHelper.ACTION_SAVED_VIDEO_CHANGE)
            BroadcastHelper.sendBroadcast(BroadcastHelper.ACTION_PLAY_VIDEO_CHANGE)
        }
    }

    fun deleteSavedVideo(videoList: List<SavedVideoInfo>) {
        scope.launch {
            MsDataBase.database.savedVideoDao().delete(videoList)
            var hasPlay = false
            videoList.forEach {
                File(it.videoPath).delete()
                if (!it.isPlayed) {
                    hasPlay = true
                }
            }
            BroadcastHelper.sendBroadcast(BroadcastHelper.ACTION_SAVED_VIDEO_CHANGE)
            if (hasPlay) {
                BroadcastHelper.sendBroadcast(BroadcastHelper.ACTION_PLAY_VIDEO_CHANGE)
            }
        }
    }

    fun likeVideo(videoInfo: SavedVideoInfo) {
        videoInfo.isLiked = !videoInfo.isLiked
        scope.launch {
            MsDataBase.database.savedVideoDao().insert(videoInfo)
        }
    }

    fun playVideo(context: Context, videoPath: String) {
        if (videoPath.isEmpty()) return
        try {
            playVideo(context, File(videoPath))
            scope.launch {
                val info = MsDataBase.database.savedVideoDao().getSavedVideoInfo(videoPath)
                if (info != null) {
                    info.isPlayed = true
                    MsDataBase.database.savedVideoDao().insert(info)
                    BroadcastHelper.sendBroadcast(BroadcastHelper.ACTION_PLAY_VIDEO_CHANGE)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playVideo(context: Context, file: File) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.setDataAndType(
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.FileProvider",
                file
            ), "video/*"
        )
        context.startActivity(intent)
    }

    fun isValidVideoUrl(videoUrl: String?): Boolean {
        if (videoUrl.isNullOrEmpty() || !Patterns.WEB_URL.matcher(videoUrl).matches()) return false
        val host = videoUrl.toUri().host.orEmpty().ifEmpty { videoUrl }
        if (AppChannelHelper.isPro) {
            return host.contains("tiktok", true) || host.contains(
                "instagram",
                true
            ) || host.contains("snapchat", true) || host.contains(
                "xhslink",
                true
            ) || host.contains("xiaohongshu", true) || host.contains(
                "twitter",
                true
            ) || host.contains("x.com", true) || host.contains("facebook", true)
        } else {
            return host.contains("instagram", true) || host.contains("x.com", true)
        }
    }
}