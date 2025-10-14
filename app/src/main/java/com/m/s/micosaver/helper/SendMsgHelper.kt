package com.m.s.micosaver.helper

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.firebase.messaging.FirebaseMessaging
import com.m.s.micosaver.BuildConfig
import com.m.s.micosaver.Constant
import com.m.s.micosaver.R
import com.m.s.micosaver.ex.scope
import com.m.s.micosaver.firebase.FirebaseHelper
import com.m.s.micosaver.ms
import com.m.s.micosaver.ui.activity.MsSplashActivity
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlin.compareTo
import kotlin.math.abs

object SendMsgHelper {
    private var msgId = 89493
    private var requestCode = 84300
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

    fun sendMsg(msgId: Int, msgType: MsgType, view1: RemoteViews, view2: RemoteViews?) {
        val manager = NotificationManagerCompat.from(ms)
        try {
            setMsgChannel(manager, msgId, msgType)
            manager.notify(msgId, createNotification(msgId, msgType, view1, view2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotification(
        msgId: Int,
        msgType: MsgType,
        view1: RemoteViews,
        view2: RemoteViews?
    ): Notification {
        val builder = NotificationCompat.Builder(ms, "ms_channel_$msgId")
        builder.setSmallIcon(R.mipmap.ms_ic_launcher)
        builder.setAutoCancel(msgType != MsgType.NO_CANCEL)
        builder.setOngoing(false)
        builder.setGroupSummary(false)
        builder.setGroup("ms_group_$msgId")
        if (msgType != MsgType.HEIGHT) {
            builder.setVibrate(null)
            builder.setSound(null)
        } else {
            builder.setStyle(NotificationCompat.BigPictureStyle())
            builder.setVibrate(longArrayOf(0, 1000))
            builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        }
        if (view2 != null) {
            builder.setCustomBigContentView(view2)
            builder.setCustomHeadsUpContentView(view2)
        } else {
            builder.setCustomBigContentView(view1)
            builder.setCustomHeadsUpContentView(view1)
        }
        builder.setContent(view1)
        builder.setCustomContentView(view1)
        return builder.build()
    }

    private fun setMsgChannel(manager: NotificationManagerCompat, msgId: Int, msgType: MsgType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    "ms_channel_$msgId",
                    ms.getString(R.string.ms_app_name),
                    if (msgType != MsgType.HEIGHT) {
                        NotificationManager.IMPORTANCE_DEFAULT
                    } else {
                        NotificationManager.IMPORTANCE_HIGH
                    }
                ).apply {
                    if (msgType != MsgType.HEIGHT) {
                        setSound(null, null)
                        enableLights(false)
                        enableVibration(false)
                    } else {
                        enableLights(true)
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0, 1000)
                    }
                }
            )
        }
    }

    enum class MsgType {
        HEIGHT,
        DEFAULT,
        NO_CANCEL
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