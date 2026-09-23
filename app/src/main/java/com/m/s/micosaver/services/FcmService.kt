package com.m.s.micosaver.services

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Base64
import android.util.Log
import androidx.core.graphics.createBitmap
import com.bumptech.glide.Glide
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.m.s.micosaver.R
import com.m.s.micosaver.channel.AppChannelHelper
import com.m.s.micosaver.ex.scope
import com.m.s.micosaver.firebase.FirebaseHelper
import com.m.s.micosaver.helper.ConditionalPollingManager
import com.m.s.micosaver.helper.FcmNotificationConditions
import com.m.s.micosaver.helper.LifecycleHelper
import com.m.s.micosaver.helper.NotificationIntervalLimiter
import com.m.s.micosaver.helper.ParamsHelper
import com.m.s.micosaver.helper.RecommendationNotificationSender
import com.m.s.micosaver.helper.SendMsgHelper
import com.m.s.micosaver.helper.shouldSendPermanentRecommendation
import com.m.s.micosaver.ms
import com.m.s.micosaver.utils.Tools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

class FcmService : FirebaseMessagingService() {
    private val TAG = "FcmService"

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        Log.d(TAG, "onMessageReceived: ${data}")
        FirebaseHelper.logEvent("ms_receive_msg")
        ConditionalPollingManager.beginDirectFcmHandling()
        ConditionalPollingManager.saveFcmMessage(data)
        ConditionalPollingManager.start("fcm_message", enqueueImmediate = false)

        val fcmType = data["msg_type"]?.toIntOrNull() ?: 0
        when(fcmType){
            0 ->{//视频
                Log.i(TAG, "receive video message")
                FcmMsgHelper.sendMsg(data) {
                    ConditionalPollingManager.finishDirectFcmHandling("fcm_video_complete")
                }
            }
            1 ->{
                Log.i(TAG, "receive permanent message")
                FirebaseHelper.logEvent("fcm_message_send_permanent_notice")
                Tools.startForegroundService()
                sendPermanentRecommendation()
            }
            else -> ConditionalPollingManager.finishDirectFcmHandling("fcm_unknown_type")
        }

    }

    private fun sendPermanentRecommendation() {
        val canSend = shouldSendPermanentRecommendation(
            isPurchaseUser = AppChannelHelper.isPro,
            isForeground = LifecycleHelper.isForeground,
            intervalAllowed = NotificationIntervalLimiter.canSend(
                NotificationIntervalLimiter.FCM_PUSH,
            ),
        )
        if (!canSend) {
            Log.i(TAG, "permanent recommendation sent=false reason=condition")
            ConditionalPollingManager.finishDirectFcmHandling("fcm_permanent_complete")
            return
        }
        scope.launch {
            try {
                RecommendationNotificationSender.send(
                    logType = NotificationIntervalLimiter.FCM_PUSH,
                    intervalScene = NotificationIntervalLimiter.FCM_PUSH,
                    finalCheck = {
                        shouldSendPermanentRecommendation(
                            isPurchaseUser = AppChannelHelper.isPro,
                            isForeground = LifecycleHelper.isForeground,
                            intervalAllowed = NotificationIntervalLimiter.canSend(
                                NotificationIntervalLimiter.FCM_PUSH,
                            ),
                        )
                    },
                )
            } finally {
                ConditionalPollingManager.finishDirectFcmHandling("fcm_permanent_complete")
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "onNewToken: $token")
//        if (token != ms.data.fcmToken) {
//            SendMsgHelper.fcmToken.upload(1)
//        }
    }

    object FcmMsgHelper {
        private const val TAG = "FcmMsgHelper"

        private val msgIdList by lazy {
            listOf(
                SendMsgHelper.getMsgId(),
                SendMsgHelper.getMsgId(),
                SendMsgHelper.getMsgId(),
                SendMsgHelper.getMsgId(),
                SendMsgHelper.getMsgId()
            )
        }
        private var idIndex = 0

        fun sendMsg(msg: Map<String, String>, onComplete: () -> Unit = {}) {
            setAppChannel(msg)
            val blockedReason = FcmNotificationConditions.blockedReason(
                msg,
                trackFcmAnalytics = true,
            )
            if (blockedReason != null) {
                Log.i(TAG, "drop video notification: $blockedReason")
                onComplete()
                return
            }

            FirebaseHelper.logEvent("ms_receive_send")
            startSend(msg, onComplete)
        }

        private fun startSend(msg: Map<String, String>, onComplete: () -> Unit) {
            val videoInfo = msg["video_info"]
            if (videoInfo.isNullOrEmpty()) {
                logEventFail("video_empty")
                onComplete()
                return
            }
            scope.launch {
                try {
                    val array = JSONArray(String(Base64.decode(videoInfo, Base64.NO_WRAP)))
                    if (array.length() <= 0) {
                        logEventFail("video_empty")
                    } else {
                        startSend(msg, array)
                    }
                } catch (e: Exception) {
                    Log.i(TAG, "parse video message failed", e)
                    logEventFail(e.message)
                } finally {
                    onComplete()
                }
            }
        }

        private suspend fun startSend(msg: Map<String, String>, array: JSONArray) {
            val contentList = getMsgContent()
            for (index in 0 until array.length()) {
                val json = array.getJSONObject(index)
                val parseUrl = json.getString("or_url")
                val coverUrl = json.getString("cover")
                val content = getMsgContent(contentList)
                sendMsg(msg, parseUrl, coverUrl, content)
            }
        }

        private suspend fun sendMsg(
            msg: Map<String, String>,
            parseUrl: String,
            coverUrl: String,
            content: Pair<Int, Int>,
        ) {
            val msgId = getMsgId()
            val coverBitmap = createCoverBitmap(coverUrl)
            withContext(Dispatchers.Main) {
                val blockedReason = FcmNotificationConditions.blockedReason(msg)
                if (blockedReason != null) {
                    Log.i(TAG, "drop video notification before send: $blockedReason")
                    return@withContext
                }
                val intent = SendMsgHelper.createMsgIntent(msgId).apply {
                    putExtra(ParamsHelper.KEY_ENTER_TYPE, ParamsHelper.EnterType.PARSE.type)
                    putExtra(ParamsHelper.KEY_PARSE_URL, parseUrl)
                }
                val isSent = SendMsgHelper.sendRecommendMsg(
                    msgId,
                    coverBitmap,
                    ms.getString(content.first),
                    ms.getString(content.second),
                    intent,
                    NotificationIntervalLimiter.FCM_PUSH,
                )
                if (isSent) {
                    NotificationIntervalLimiter.recordSent(NotificationIntervalLimiter.FCM_PUSH)
                    FirebaseHelper.logEvent("ms_send_msg_suc", Bundle().apply {
                        putString("type", ParamsHelper.EnterType.PARSE.type)
                    })
                } else {
                    logEventFail("notify_failed")
                }
            }
        }

        private fun createCoverBitmap(coverUrl: String): Bitmap? {
            var resultBitmap: Bitmap? = null
            try {
                val drawable =
                    Glide.with(ms).asDrawable().load(coverUrl).submit().get() ?: return null
                if (drawable is BitmapDrawable) {
                    resultBitmap = drawable.bitmap
                } else {
                    resultBitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
                    val canvas = Canvas(resultBitmap)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
            return resultBitmap
        }

        private fun getMsgContent(list: List<Pair<Int, Int>>): Pair<Int, Int> {
            val index = ms.data.msgContentIndex % list.size
            ms.data.msgContentIndex = index + 1
            return list[index]
        }

        private fun logEventFail(msg: String?) {
            FirebaseHelper.logEvent("ms_send_fail", Bundle().apply {
                putString("msg", msg)
            })
        }

        private fun setAppChannel(msg: Map<String, String>) {
            val time = msg["ts"]
            if (time.isNullOrEmpty()) return
            try {
                val timeLong = time.toLong() * DateUtils.SECOND_IN_MILLIS
                if (timeLong <= 0) return
                if (ms.data.firstReceiveMsgTime <= 0) {
                    ms.data.firstReceiveMsgTime = timeLong
                } else {
                    checkAppChannelTime(timeLong)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun checkAppChannelTime(time: Long) {
            if (AppChannelHelper.isPro) return
            val maxTime = FirebaseHelper.remoteConfig.channelMaxTime
            if (maxTime <= 0) return
            if (time - ms.data.firstReceiveMsgTime >= maxTime) {
                ms.data.currentProKey = "ms_key_pp"
                FirebaseHelper.setEventPro()
                FirebaseHelper.logEvent("ms_pp_time")
            }
        }

        private fun getMsgId(): Int {
            return msgIdList[idIndex].also {
                idIndex = (idIndex + 1) % msgIdList.size
            }
        }

        private fun getMsgContent(): List<Pair<Int, Int>> {
            return listOf(
                R.string.ms_msg_desc1927 to R.string.ms_msg_act1927,
                R.string.ms_msg_desc1928 to R.string.ms_msg_act1928,
                R.string.ms_msg_desc1929 to R.string.ms_msg_act1929,
                R.string.ms_msg_desc1930 to R.string.ms_msg_act1930,
                R.string.ms_msg_desc1931 to R.string.ms_msg_act1931,
                R.string.ms_msg_desc1932 to R.string.ms_msg_act1932,
                R.string.ms_msg_desc1933 to R.string.ms_msg_act1933,
                R.string.ms_msg_desc1934 to R.string.ms_msg_act1934,
                R.string.ms_msg_desc1935 to R.string.ms_msg_act1935,
                R.string.ms_msg_desc1936 to R.string.ms_msg_act1936,
                R.string.ms_msg_desc1937 to R.string.ms_msg_act1937,
                R.string.ms_msg_desc1938 to R.string.ms_msg_act1938,
                R.string.ms_msg_desc1939 to R.string.ms_msg_act1939,
                R.string.ms_msg_desc1940 to R.string.ms_msg_act1940,
                R.string.ms_msg_desc1941 to R.string.ms_msg_act1941,
                R.string.ms_msg_desc1942 to R.string.ms_msg_act1942,
                R.string.ms_msg_desc1943 to R.string.ms_msg_act1943,
                R.string.ms_msg_desc1944 to R.string.ms_msg_act1944,
                R.string.ms_msg_desc1945 to R.string.ms_msg_act1945,
                R.string.ms_msg_desc1946 to R.string.ms_msg_act1946,
                R.string.ms_msg_desc1947 to R.string.ms_msg_act1947,
                R.string.ms_msg_desc1948 to R.string.ms_msg_act1948,
                R.string.ms_msg_desc1949 to R.string.ms_msg_act1949,
                R.string.ms_msg_desc1950 to R.string.ms_msg_act1950,
                R.string.ms_msg_desc1951 to R.string.ms_msg_act1951,
                R.string.ms_msg_desc1952 to R.string.ms_msg_act1952,
                R.string.ms_msg_desc1953 to R.string.ms_msg_act1953,
                R.string.ms_msg_desc1954 to R.string.ms_msg_act1954,
                R.string.ms_msg_desc1955 to R.string.ms_msg_act1955,
                R.string.ms_msg_desc1956 to R.string.ms_msg_act1956,
                R.string.ms_msg_desc1957 to R.string.ms_msg_act1957,
                R.string.ms_msg_desc1958 to R.string.ms_msg_act1958,
                R.string.ms_msg_desc1959 to R.string.ms_msg_act1959,
                R.string.ms_msg_desc1960 to R.string.ms_msg_act1960,
                R.string.ms_msg_desc1961 to R.string.ms_msg_act1961,
                R.string.ms_msg_desc1962 to R.string.ms_msg_act1962,
                R.string.ms_msg_desc1963 to R.string.ms_msg_act1963,
                R.string.ms_msg_desc1964 to R.string.ms_msg_act1964,
                R.string.ms_msg_desc1965 to R.string.ms_msg_act1965,
                R.string.ms_msg_desc1966 to R.string.ms_msg_act1966,
                R.string.ms_msg_desc1967 to R.string.ms_msg_act1967,
                R.string.ms_msg_desc1968 to R.string.ms_msg_act1968,
                R.string.ms_msg_desc1969 to R.string.ms_msg_act1969,
                R.string.ms_msg_desc1970 to R.string.ms_msg_act1970,
                R.string.ms_msg_desc1971 to R.string.ms_msg_act1971,
                R.string.ms_msg_desc1972 to R.string.ms_msg_act1972,
                R.string.ms_msg_desc1973 to R.string.ms_msg_act1973,
                R.string.ms_msg_desc1974 to R.string.ms_msg_act1974,
                R.string.ms_msg_desc1975 to R.string.ms_msg_act1975,
                R.string.ms_msg_desc1976 to R.string.ms_msg_act1976,
                R.string.ms_msg_desc1977 to R.string.ms_msg_act1977,
                R.string.ms_msg_desc1978 to R.string.ms_msg_act1978,
                R.string.ms_msg_desc1979 to R.string.ms_msg_act1979

            )
        }
    }
}
