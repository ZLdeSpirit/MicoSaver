package com.m.s.micosaver.helper

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.firebase.messaging.FirebaseMessaging
import com.m.s.micosaver.BuildConfig
import com.m.s.micosaver.Constant
import com.m.s.micosaver.R
import com.m.s.micosaver.broadcast.NotificationDismissReceiver
import com.m.s.micosaver.channel.AppChannelHelper
import com.m.s.micosaver.ex.scope
import com.m.s.micosaver.firebase.FirebaseHelper
import com.m.s.micosaver.ms
import com.m.s.micosaver.ui.activity.MsSplashActivity
import com.m.s.micosaver.utils.Tools
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlin.math.abs

object SendMsgHelper {
    const val CIRCLE_NOTICE_TAG = "CircleNotice"
    private const val FCM_CHANNEL_ID = "ms_fcm_heads_up_v2"
    private const val FCM_SILENT_CHANNEL_ID = "ms_fcm_heads_up_silent_v1"
    private const val DOWNLOAD_CHANNEL_ID = "ms_download"

    private var msgId = 89493
    private var requestCode = 84300
    private val circleTasks = ConcurrentHashMap<Int, CircleTask>()
    val fcmToken by lazy { FcmToken() }

    fun getRequestCode(): Int {
        return requestCode++
    }

    fun getMsgId(): Int {
        return msgId++
    }

    fun createMsgIntent(msgId: Int): Intent {
        return Intent(ms, MsSplashActivity::class.java).apply {
            `package` = ms.packageName
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(ParamsHelper.KEY_MSG_ID, msgId)
        }
    }

    @SuppressLint("MissingPermission")
    fun sendMsg(msgId: Int, msgType: MsgType, smallLayout: RemoteViews, mediumLayout: RemoteViews?,bigLayout: RemoteViews?, alertText: String): Boolean {
        return sendMsg(
            msgId,
            msgType,
            smallLayout,
            mediumLayout,
            bigLayout,
            alertText,
            silent = false,
            deleteIntent = null,
        )
    }

    @SuppressLint("MissingPermission")
    private fun sendMsg(
        msgId: Int,
        msgType: MsgType,
        smallLayout: RemoteViews,
        mediumLayout: RemoteViews?,
        bigLayout: RemoteViews?,
        alertText: String,
        silent: Boolean,
        deleteIntent: PendingIntent?,
    ): Boolean {
        val manager = NotificationManagerCompat.from(ms)
        return try {
            manager.notify(
                msgId,
                createNotification(
                    msgId,
                    msgType,
                    smallLayout,
                    mediumLayout,
                    bigLayout,
                    alertText,
                    silent,
                    deleteIntent,
                ),
            )
            true
        } catch (e: Exception) {
            Log.e("SendMsgHelper", "send notification failed", e)
            false
        }
    }

    fun sendRecommendMsg(
        msgId: Int,
        image: Bitmap?,
        title: String,
        action: String,
        intent: Intent,
        source: String,
    ): Boolean {
        if (!ms.isOpenMsg) {
            if (!AppChannelHelper.isPro) {
                MediaNoticeManager.cancelActiveIfExists("non_purchase_user")
                Log.i(MediaNoticeManager.TAG, "sent=false reason=non_purchase_user")
                return false
            }
            if (!FirebaseHelper.remoteConfig.getMediaNoticeSwitch()) {
                MediaNoticeManager.cancelActiveIfExists("switch_off")
                Log.i(MediaNoticeManager.TAG, "sent=false reason=switch_off")
                return false
            }
            val sent = MediaNoticeManager.send(
                title,
                action,
                Intent(intent).putExtra(
                    ParamsHelper.KEY_MSG_ID,
                    MediaNoticeManager.NOTIFICATION_ID,
                ),
                resolveCircleNoticeConfig(FirebaseHelper.remoteConfig.getCircleNoticeConfig()),
                source,
            )
            if (sent) stopCircleNoticeLoops("replaced_by_media_notice")
            return sent
        }
        MediaNoticeManager.cancelActiveIfExists("notification_permission_granted")
        val pendingIntent = PendingIntent.getActivity(
            ms,
            getRequestCode(),
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )
        val deleteIntent = PendingIntent.getBroadcast(
            ms,
            getRequestCode(),
            Intent(ms, NotificationDismissReceiver::class.java).apply {
                putExtra(ParamsHelper.KEY_MSG_ID, msgId)
            },
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            },
        )
        val smallLayout = RemoteViews(ms.packageName, R.layout.ms_notification_small).apply {
            setImageViewBitmap(R.id.imageIv, image)
            setTextViewText(R.id.titleTv, title)
            setOnClickPendingIntent(R.id.notificationRoot, pendingIntent)
        }
        val mediumLayout = RemoteViews(
            ms.packageName,
            if (Tools.isSamsungOneUi4()) {
                R.layout.ms_notification_sa_mediaum
            } else {
                R.layout.ms_notification_medium
            }
        ).apply {
            setImageViewBitmap(R.id.imageIv, image)
            setTextViewText(R.id.titleTv, title)
            setTextViewText(R.id.actionBtnText, action)
            setOnClickPendingIntent(R.id.notificationRoot, pendingIntent)
        }
        val bigLayout = RemoteViews(ms.packageName, R.layout.ms_notification_big).apply {
            setImageViewBitmap(R.id.imageIv, image)
            setTextViewText(R.id.titleTv, title)
            setTextViewText(R.id.actionBtnText, action)
            setOnClickPendingIntent(R.id.notificationContainer, pendingIntent)
        }
        val config = resolveCircleNoticeConfig(FirebaseHelper.remoteConfig.getCircleNoticeConfig())
        val firstSent = sendMsg(
            msgId,
            MsgType.HEIGHT,
            smallLayout,
            mediumLayout,
            bigLayout,
            title,
            silent = false,
            deleteIntent = deleteIntent,
        )
        Log.i(
            CIRCLE_NOTICE_TAG,
            "source=$source id=$msgId current=1 total=${config.circleCount} " +
                "intervalMs=${config.intervalMillis} silent=false sent=$firstSent",
        )
        if (firstSent) {
            stopCircleNoticeLoops("replaced_by_new_notice")
        }
        if (firstSent && config.circleCount > 1) {
            startCircleNotice(
                msgId,
                config,
                smallLayout,
                mediumLayout,
                bigLayout,
                title,
                deleteIntent,
                source,
            )
        } else if (firstSent) {
            Log.i(
                CIRCLE_NOTICE_TAG,
                "source=$source id=$msgId loop=false reason=circle_count_one",
            )
        }
        return firstSent
    }

    private fun stopCircleNoticeLoops(reason: String) {
        circleTasks.entries.toList().forEach { (id, task) ->
            if (circleTasks.remove(id, task)) {
                task.job.cancel()
                Log.i(
                    CIRCLE_NOTICE_TAG,
                    "source=${task.source} id=$id stopped reason=$reason " +
                        "current=${task.current} total=${task.total}",
                )
            }
        }
    }

    fun cancelCircleNotice(msgId: Int, reason: String) {
        if (msgId <= 0) return
        if (MediaNoticeManager.cancelIfMediaNotice(msgId, reason)) return
        val task = circleTasks.remove(msgId)
        task?.job?.cancel()
        NotificationManagerCompat.from(ms).cancel(msgId)
        Log.i(
            CIRCLE_NOTICE_TAG,
            "source=${task?.source ?: "unknown"} id=$msgId stopped reason=$reason " +
                "current=${task?.current ?: "finished"} " +
                "total=${task?.total ?: "finished"}",
        )
    }

    private fun startCircleNotice(
        msgId: Int,
        config: CircleNoticeConfig,
        smallLayout: RemoteViews,
        mediumLayout: RemoteViews?,
        bigLayout: RemoteViews,
        title: String,
        deleteIntent: PendingIntent,
        source: String,
    ) {
        val task = CircleTask(source = source, current = 1, total = config.circleCount)
        task.job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                for (current in 2..config.circleCount) {
                    delay(config.intervalMillis)
                    task.current = current
                    val sent = sendMsg(
                        msgId,
                        MsgType.HEIGHT,
                        smallLayout,
                        mediumLayout,
                        bigLayout,
                        title,
                        silent = true,
                        deleteIntent = deleteIntent,
                    )
                    Log.i(
                        CIRCLE_NOTICE_TAG,
                        "source=$source id=$msgId current=$current total=${config.circleCount} " +
                            "silent=true sent=$sent",
                    )
                    if (!sent) break
                }
            } finally {
                if (circleTasks.remove(msgId, task)) {
                    Log.i(
                        CIRCLE_NOTICE_TAG,
                        "source=$source id=$msgId loop=finished " +
                            "current=${task.current} total=${task.total}",
                    )
                }
            }
        }
        circleTasks.put(msgId, task)?.job?.cancel()
        Log.i(
            CIRCLE_NOTICE_TAG,
            "source=$source id=$msgId loop=started total=${config.circleCount} " +
                "intervalMs=${config.intervalMillis}",
        )
        task.job.start()
    }

    private fun createNotification(
        msgId: Int,
        msgType: MsgType,
        small: RemoteViews,
        medium: RemoteViews?,
        big: RemoteViews?,
        alertText: String,
        silent: Boolean = false,
        deleteIntent: PendingIntent? = null,
    ): Notification {
        val display = big ?: small
        val headsUp = medium ?: small

        val builder = NotificationCompat.Builder(ms, getChannelId(msgType, silent))
        builder.setSmallIcon(R.mipmap.ms_ic_launcher)
        builder.setContentTitle(ms.getString(R.string.ms_app_name))
        builder.setContentText(alertText)
        builder.setTicker(alertText)

        builder.setContent(small)
        builder.setCustomContentView(small)
        builder.setCustomHeadsUpContentView(headsUp)
        builder.setCustomBigContentView(display)
        builder.setPriority(
            if (msgType == MsgType.HEIGHT) NotificationCompat.PRIORITY_MAX
            else NotificationCompat.PRIORITY_DEFAULT
        )
        builder.setCategory(NotificationCompat.CATEGORY_MESSAGE)
        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        deleteIntent?.let {
            builder.setDeleteIntent(it)
        }

        builder.setAutoCancel(msgType != MsgType.NO_CANCEL)
        builder.setOngoing(false)
        builder.setGroupSummary(false)
        builder.setGroup("ms_group_$msgId")
        if (msgType != MsgType.HEIGHT || silent) {
            builder.setVibrate(null)
            builder.setSound(null)
        } else {
            builder.setStyle(NotificationCompat.BigPictureStyle())
            builder.setVibrate(longArrayOf(0, 1000))
            builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        }
        return builder.build()
    }

    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = NotificationManagerCompat.from(ms)
            manager.createNotificationChannels(
                listOf(
                    NotificationChannel(
                        FCM_CHANNEL_ID,
                        ms.getString(R.string.ms_app_name),
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        enableLights(true)
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0, 1000)
                    },
                    NotificationChannel(
                        FCM_SILENT_CHANNEL_ID,
                        ms.getString(R.string.ms_app_name),
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply {
                        setSound(null, null)
                        enableLights(true)
                        enableVibration(false)
                        vibrationPattern = null
                    },
                    NotificationChannel(
                        DOWNLOAD_CHANNEL_ID,
                        ms.getString(R.string.ms_downloading),
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        setSound(null, null)
                        enableLights(false)
                        enableVibration(false)
                    },
                )
            )
        }
    }

    private fun getChannelId(msgType: MsgType, silent: Boolean): String = when {
        msgType != MsgType.HEIGHT -> DOWNLOAD_CHANNEL_ID
        silent -> FCM_SILENT_CHANNEL_ID
        else -> FCM_CHANNEL_ID
    }

    enum class MsgType {
        HEIGHT,
        DEFAULT,
        NO_CANCEL
    }

    private class CircleTask(
        val source: String,
        @Volatile var current: Int,
        val total: Int,
    ) {
        lateinit var job: Job
    }

    class FcmToken {
        private var isUploading = false
        private val handler = Handler(Looper.getMainLooper())
        private var retryCount = 0
        private val uploadRunnable = Runnable {
            startUpload()
        }
        private val uploadUrl: String
            get() {
                return Constant.UPLOAD_TOKEN_URL
            }

        fun upload(type: Int) {
            if (isUploading || (type == 0 && abs(System.currentTimeMillis() - ms.data.fcmTokenTime) < DateUtils.DAY_IN_MILLIS)) {
                return
            }
            retryCount = 0
            isUploading = true
            handler.removeCallbacks(uploadRunnable)
            startUpload()
        }

        private fun startUpload() {
            FirebaseHelper.logEvent("ms_uplo_token")
            try {
                FirebaseMessaging.getInstance().token.addOnCompleteListener {
                    val token = if (it.isSuccessful) it.result else ""
                    if (token.isEmpty() && ms.data.isUploadedClack) {
                        startRetry("token_null")
                        return@addOnCompleteListener
                    }
                    uploadToken(token)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                startRetry(e.message)
            }
        }

        private fun uploadToken(token: String) {
            scope.launch {
                try {
                    val adId = try {
                        AdvertisingIdClient.getAdvertisingIdInfo(ms).id.orEmpty()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        ""
                    }
                    val params = JSONObject().apply {
                        put("sie", adId)
                        put("bzd", ms.appInstallTime)
                        if (token.isNotEmpty()) {
                            put("lche", token)
                        }
                        ms.data.appMarketChannel?.let {
                            if (it.isNotEmpty()) {
                                put("cha", it)
                            }
                        }
                    }.toString()
                    val request = Request.Builder()
                        .url(uploadUrl)
                        .post(params.toRequestBody("application/json".toMediaType()))
                        .addHeader("SQA", ms.packageName)
                        .addHeader("SQC", BuildConfig.VERSION_NAME)
                        .build()
                    val result = createClient().newCall(request).execute()
                    if (result.isSuccessful) {
                        ms.data.isUploadedClack = true
                        if (token.isNotEmpty()) {
                            ms.data.fcmTokenTime = System.currentTimeMillis()
                            ms.data.fcmToken = token
                            Log.i("SendMsgHelper", "uploadToken success")
                            FirebaseHelper.logEvent("ms_uplo_token_success")
                            return@launch
                        }
                        startRetry("token_null")
                        return@launch
                    }
                    startRetry(result.code.toString())
                } catch (e: Exception) {
                    e.printStackTrace()
                    startRetry(e.message)
                }
            }
        }

        private fun createClient(): OkHttpClient {
            val builder = OkHttpClient.Builder()
            try {
                val trustManager = @SuppressLint("CustomX509TrustManager")
                object : X509TrustManager {
                    override fun checkClientTrusted(
                        chain: Array<out X509Certificate>?,
                        authType: String?
                    ) = Unit

                    override fun checkServerTrusted(
                        chain: Array<out X509Certificate>?,
                        authType: String?
                    ) = Unit

                    override fun getAcceptedIssuers(): Array<X509Certificate> {
                        return arrayOf()
                    }
                }
                val sslContext = SSLContext.getInstance("SSL")
                sslContext.init(null, arrayOf(trustManager), SecureRandom())
                builder.sslSocketFactory(sslContext.socketFactory, trustManager)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            builder.hostnameVerifier { _, _ -> true }
            return builder.build()
        }

        private fun startRetry(msg: String?) {
            Log.i("SendMsgHelper", "uploadToken failed, msg: $msg")
            FirebaseHelper.logEvent("ms_uplo_token_failed", Bundle().apply {
                putString("msg", msg)
            })
            retryCount++
            if (retryCount >= 6) {
                isUploading = false
                return
            }
            handler.postDelayed(uploadRunnable, 8200)
        }

    }

}

internal data class CircleNoticeConfig(
    val circleCount: Int = 15,
    val intervalMillis: Long = 4_000L,
)

internal fun resolveCircleNoticeConfig(config: Map<String, Long>?): CircleNoticeConfig {
    val count = config?.get(FirebaseHelper.remoteConfig.CIRCLE_COUNT)
        ?: CircleNoticeConfig().circleCount.toLong()
    val intervalSeconds = config?.get(FirebaseHelper.remoteConfig.INTERVAL_TIME)
        ?: CircleNoticeConfig().intervalMillis / 1_000L
    return CircleNoticeConfig(
        circleCount = count.coerceAtLeast(1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        intervalMillis = if (intervalSeconds < 1L) 4_000L else intervalSeconds * 1_000L,
    )
}

fun RemoteViews.setOnClickPendingIntent(viewId: Int, intent: Intent): PendingIntent {
    val pendingIntent = PendingIntent.getActivity(
        ms,
        SendMsgHelper.getRequestCode(),
        intent,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
    )
    setOnClickPendingIntent(viewId, pendingIntent)
    return pendingIntent
}
