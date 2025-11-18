package com.m.s.micosaver.db.info

import com.m.s.micosaver.R
import com.m.s.micosaver.db.MsDataBase
import com.m.s.micosaver.ex.scope
import com.m.s.micosaver.firebase.FirebaseHelper
import com.m.s.micosaver.helper.LifecycleHelper
import com.m.s.micosaver.helper.ParamsHelper
import com.m.s.micosaver.helper.SendMsgHelper
import com.m.s.micosaver.helper.VideoHelper
import com.m.s.micosaver.helper.setOnClickPendingIntent
import com.m.s.micosaver.ms
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.Looper
import android.widget.RemoteViews
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.createBitmap
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.bumptech.glide.Glide
import com.liulishuo.okdownload.DownloadTask
import com.liulishuo.okdownload.core.cause.ResumeFailedCause
import com.liulishuo.okdownload.core.listener.DownloadListener3
import com.m.s.micosaver.ad.AdHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

@Entity(tableName = "ms_saving_video_info")
class SavingVideoInfo(
    @ColumnInfo(name = "ms_parse_url") val parseUrl: String,
    @ColumnInfo(name = "ms_downl_url") val downloadUrl: String,
    @ColumnInfo(name = "ms_cover_url") val coverUrl: String,
    @ColumnInfo(name = "ms_video_desc") val videoDesc: String,
    @ColumnInfo(name = "ms_downl_header") val downloadHeader: String?,
    @PrimaryKey(autoGenerate = false) @ColumnInfo(name = "ms_id") val id: Long = System.currentTimeMillis()
) {

    @ColumnInfo("ms_aut_nm")
    var authorName: String = ""

    @ColumnInfo(name = "ms_downl_progress")
    var downloadProgress = 0L

    @ColumnInfo(name = "ms_total_length")
    var totalLength = 0L

    @ColumnInfo(name = "ms_local_path")
    var localPath = ""

    @Ignore
    var isSaving = false

    @ColumnInfo(name = "ms_is_finish")
    var isFinish = false

    @ColumnInfo(name = "ms_duration")
    var duration : Long = 0L

    @get:Ignore
    private val downloadTask by lazy {
        ProDownloadTask()
    }

    @get:Ignore
    val progress: Int
        get() {
            if (totalLength <= 0) return 0
            return (downloadProgress * 100f / totalLength).toInt()
        }

    fun showCoverUrl(): String {
        if (isFinish) return localPath
        return coverUrl.ifEmpty { downloadUrl }
    }

    fun startDownload() {
        downloadTask.start()
    }

    fun pauseDownload() {
        downloadTask.pause()
    }

    fun deleteDownload() {
        downloadTask.delete()
    }

    fun registerDownloadListener(listener: OnDownloadListener) {
        downloadTask.registerDownloadListener(listener)
    }

    fun unregisterDownloadListener(listener: OnDownloadListener) {
        downloadTask.unregisterDownloadListener(listener)
    }

    private fun createDownloadHeaders(): Map<String, List<String>>? {
        if (downloadHeader.isNullOrEmpty()) return null
        try {
            val json = JSONObject(downloadHeader)
            val map = hashMapOf<String, MutableList<String>>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = json.get(key)
                if (value is JSONArray) {
                    val list = mutableListOf<String>()
                    for (i in 0 until value.length()) {
                        list.add(value.getString(i))
                    }
                    map[key] = list
                } else {
                    map[key] = mutableListOf(value.toString())
                }
            }
            return map
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun saveInfo() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            scope.launch {
                MsDataBase.database.savingVideoDao().insert(this@SavingVideoInfo)
            }
        } else {
            MsDataBase.database.savingVideoDao().insert(this@SavingVideoInfo)
        }
    }

    inner class ProDownloadTask {

        private val msgId = SendMsgHelper.getMsgId()
        private var msgBitmap: Bitmap? = null
        private var isLoadingMsgBitmap = false
        private var downloadCallbacks: DownloadCallbacks? = null
        private var downloadTask: DownloadTask? = null
        private val downloadListenerList = mutableListOf<OnDownloadListener>()

        val downloadFile: File
            get() {
                if (localPath.isNotEmpty()) return File(localPath)
                val file = File(ms.filesDir, "/video/${System.currentTimeMillis()}")
                file.parentFile?.mkdirs()
                localPath = file.absolutePath
                saveInfo()
                return file
            }

        fun start() {
            pause()
            downloadTask = createDownloadTask()
            downloadStateChange(true)
            VideoHelper.savingVideoInfo(this@SavingVideoInfo)
            downloadCallbacks = DownloadCallbacks()
            downloadTask?.enqueue(downloadCallbacks)
            AdHelper.preload(AdHelper.Position.SAVED_NATIVE)
            FirebaseHelper.logEvent("ms_downl_start")
        }

        private fun createDownloadTask(): DownloadTask {
            return DownloadTask.Builder(downloadUrl, downloadFile)
                .setMinIntervalMillisCallbackProcess(500).apply {
                    createDownloadHeaders()?.let {
                        setHeaderMapFields(it)
                    }
                }.build()
        }

        fun pause() {
            downloadTask?.cancel()
            downloadTask = null
            downloadCallbacks?.cancelDownload()
            downloadCallbacks = null
            downloadStateChange(false)
        }

        fun delete() {
            pause()
            VideoHelper.removeSavingVideoInfo(this@SavingVideoInfo)
            scope.launch {
                MsDataBase.database.savingVideoDao().delete(this@SavingVideoInfo)
                File(localPath).delete()
            }
        }

        fun registerDownloadListener(listener: OnDownloadListener) {
            if (downloadListenerList.contains(listener)) return
            downloadListenerList.add(listener)
        }

        fun unregisterDownloadListener(listener: OnDownloadListener) {
            downloadListenerList.remove(listener)
        }

        private fun downloadStateChange(isSaving: Boolean) {
            if (this@SavingVideoInfo.isSaving == isSaving) return
            this@SavingVideoInfo.isSaving = isSaving
            downloadListenerList.forEach {
                it.onStateChange(this@SavingVideoInfo)
            }
            sendDownloadMsg()
        }

        private fun downloadProgressChange(progress: Long, totalLength: Long) {
            if (downloadProgress < progress) {
                downloadProgress = progress
            }
            this@SavingVideoInfo.totalLength = totalLength
            downloadListenerList.forEach {
                it.onProgressChange(this@SavingVideoInfo)
            }
            saveInfo()
            sendDownloadMsg()
        }

        private fun downloadFinish() {
            scope.launch {
                try {
                    val file = File(localPath)
                    isFinish = true
                    totalLength = file.length()
                    saveInfo()
                    VideoHelper.addSavedVideo(localPath, totalLength, videoDesc, authorName)
                    VideoHelper.removeSavingVideoInfo(this@SavingVideoInfo)
                    withContext(Dispatchers.Main) {
                        sendDownloadMsg()
                        LifecycleHelper.showSavedDialog(this@SavingVideoInfo)
                    }
                    FirebaseHelper.logEvent("ms_downl_finish")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }


        private fun sendDownloadMsg() {
            if (!isSaving && !isFinish) {
                msgBitmap?.recycle()
                msgBitmap = null
                NotificationManagerCompat.from(ms).cancel(msgId)
                return
            }
            getMsgBitmap()
            val enterType =
                if (isFinish) ParamsHelper.EnterType.SAVED.type else ParamsHelper.EnterType.SAVING.type
            val intent = SendMsgHelper.createMsgIntent(msgId).apply {
                putExtra(ParamsHelper.KEY_ENTER_TYPE, enterType)
            }
            val remoteViews =
                if (isFinish) createSavedRemoteViews(intent) else createSavingRemoteViews(intent)
            SendMsgHelper.sendMsg(
                msgId,
                if (isFinish) SendMsgHelper.MsgType.DEFAULT else SendMsgHelper.MsgType.NO_CANCEL,
                remoteViews,
                null
            )
            if (isFinish) {
                msgBitmap?.recycle()
                msgBitmap = null
            }
            FirebaseHelper.logEvent("ms_send_msg_suc", Bundle().apply {
                putString("type", enterType)
            })
        }

        private fun createSavingRemoteViews(intent: Intent): RemoteViews {
            val remoteViews = RemoteViews(ms.packageName, R.layout.ms_notification_downloading)
            remoteViews.setTextViewText(R.id.downloadingDescTv, videoDesc)
            remoteViews.setTextViewText(R.id.viewBtn, ms.getString(R.string.ms_view))
            val progress = progress
            remoteViews.setProgressBar(R.id.downloadingProgressBar, 100, progress, false)
            remoteViews.setTextViewText(R.id.downloadingProgressTv, "$progress%")
            if (msgBitmap != null && msgBitmap?.isRecycled != true) {
                remoteViews.setImageViewBitmap(R.id.downloadingImageIv, msgBitmap)
            }
            remoteViews.setOnClickPendingIntent(R.id.downloadingContainer, intent)
            return remoteViews
        }

        private fun createSavedRemoteViews(intent: Intent): RemoteViews {
            intent.putExtra(ParamsHelper.KEY_VIDEO_PATH, localPath)
            val remoteViews = RemoteViews(ms.packageName, R.layout.ms_notification_downloaded)
            remoteViews.setTextViewText(R.id.downloadedDescTv, videoDesc)
            val author = authorName.ifEmpty { ms.getString(R.string.ms_unknown) }
            remoteViews.setTextViewText(R.id.authorTv, author)
            remoteViews.setTextViewText(R.id.playBtn, ms.getString(R.string.ms_play))
            if (msgBitmap != null && msgBitmap?.isRecycled != true) {
                remoteViews.setImageViewBitmap(R.id.downloadedImageIv, msgBitmap)
            }
            remoteViews.setOnClickPendingIntent(R.id.downloadedContainer, intent)
            return remoteViews
        }


        private fun getMsgBitmap() {
            if (isLoadingMsgBitmap || !isSaving || msgBitmap != null) return
            isLoadingMsgBitmap = true
            scope.launch {
                val resultBitmap: Bitmap?
                try {
                    val drawable =
                        Glide.with(ms).asDrawable().load(showCoverUrl()).submit().get()
                            ?: return@launch
                    if (drawable is BitmapDrawable) {
                        resultBitmap = drawable.bitmap
                    } else {
                        resultBitmap =
                            createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
                        val canvas = Canvas(resultBitmap)
                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                        drawable.draw(canvas)
                    }
                    msgBitmap = resultBitmap
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                isLoadingMsgBitmap = false
                if (msgBitmap != null) {
                    if (isSaving || isFinish) {
                        withContext(Dispatchers.Main) {
                            sendDownloadMsg()
                        }
                    } else {
                        msgBitmap?.recycle()
                        msgBitmap = null
                    }
                }
            }
        }

        inner class DownloadCallbacks : DownloadListener3() {
            private var isDownloadState = 0
            private var retryCount = 0
            private var retryJob: Job? = null

            fun cancelDownload() {
                if (isDownloadState != 0) return
                isDownloadState = 2
                cancelRetry()
            }

            private fun cancelRetry() {
                retryJob?.cancel()
                retryJob = null
            }

            private fun startRetry(task: DownloadTask) {
                if (retryCount >= 6) {
                    isDownloadState = 3
                    downloadStateChange(false)
                    return
                }
                if (retryJob?.isActive == true) return
                retryJob = scope.launch {
                    delay(3200)
                    retryCount++
                    task.enqueue(this@DownloadCallbacks)
                }
            }

            override fun retry(
                task: DownloadTask, cause: ResumeFailedCause
            ) {

            }

            override fun connected(
                task: DownloadTask,
                blockCount: Int,
                currentOffset: Long,
                totalLength: Long
            ) {
                if (isDownloadState != 0) return
                downloadProgressChange(currentOffset, totalLength)
                if (totalLength > 0 && currentOffset == totalLength) {
                    completed(task)
                }
            }

            override fun progress(
                task: DownloadTask, currentOffset: Long, totalLength: Long
            ) {
                if (isDownloadState != 0) return
                downloadProgressChange(currentOffset, totalLength)
                if (totalLength > 0 && currentOffset == totalLength) {
                    completed(task)
                }
            }

            override fun started(task: DownloadTask) {
                if (isDownloadState != 0) return
                cancelRetry()
                downloadStateChange(true)
            }

            override fun completed(task: DownloadTask) {
                if (isDownloadState != 0) return
                isDownloadState = 1
                downloadFinish()
            }

            override fun canceled(task: DownloadTask) {
                if (isDownloadState != 0) return
                downloadStateChange(false)
            }

            override fun error(task: DownloadTask, e: Exception) {
                if (isDownloadState != 0) return
                startRetry(task)
            }

            override fun warn(task: DownloadTask) {
                if (isDownloadState != 0) return
                startRetry(task)
            }

        }

    }

    interface OnDownloadListener {
        fun onStateChange(info: SavingVideoInfo)
        fun onProgressChange(info: SavingVideoInfo)
    }

}