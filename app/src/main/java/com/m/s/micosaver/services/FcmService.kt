package com.m.s.micosaver.services

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Base64
import android.util.Log
import androidx.core.graphics.createBitmap
import com.bumptech.glide.Glide
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.m.s.micosaver.channel.AppChannelHelper
import com.m.s.micosaver.ex.scope
import com.m.s.micosaver.helper.LifecycleHelper
import com.m.s.micosaver.helper.ParamsHelper
import com.m.s.micosaver.helper.NotificationIntervalLimiter
import com.m.s.micosaver.helper.SendMsgHelper
import com.m.s.micosaver.ms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Calendar
import kotlin.ranges.contains
import com.m.s.micosaver.R
import com.m.s.micosaver.ad.AdFrequencyLimiter
import com.m.s.micosaver.firebase.FirebaseHelper
import com.m.s.micosaver.utils.Tools
import java.util.Locale
import kotlin.text.toInt

class FcmService : FirebaseMessagingService() {
    private val TAG = "FcmService"

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        Log.d(TAG, "onMessageReceived: ${data}")
        FirebaseHelper.logEvent("ms_receive_msg")

        val fcmType = (data["msg_type"] ?: "0").toInt()
        when(fcmType){
            0 ->{//视频
                Log.i(TAG, "receive video message")
                FcmMsgHelper.sendMsg(data)
            }
            1 ->{
                Log.i(TAG, "receive permanent message")
                if (AppChannelHelper.isPro) {
                    FirebaseHelper.logEvent("fcm_message_send_permanent_notice")
                    Tools.startForegroundService()
                }
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

        fun sendMsg(msg: Map<String, String>) {
            setAppChannel(msg)
            if (!canSendVideoNotification()) return
            if (ms.isOpenMsg) FirebaseHelper.logEvent("ms_receive_open")
            if (!AppChannelHelper.isPro) return
            FirebaseHelper.logEvent("ms_receive_pro")
            if (LifecycleHelper.isForeground) return
            FirebaseHelper.logEvent("ms_receive_background")
            if (!checkSendTime(msg)) return
            if (!checkInstallLimit(msg["install_limit"])) return
            if (!checkCountry(msg["ctr"], msg["ex_ctr"])) return
            if (!checkVersion(msg["ver"])) return
            if (!NotificationIntervalLimiter.canSend(NotificationIntervalLimiter.FCM_PUSH)) {
                Log.i(TAG, "drop video notification: notice interval")
                return
            }

            FirebaseHelper.logEvent("ms_receive_send")
            startSend(msg)
        }

        private fun checkInstallLimit(limit: String?): Boolean {
            return try {
                if (limit.isNullOrEmpty()) return true
                val array = JSONArray(limit)
                if (array.length() < 2) return true
                val minInstallMinutes = if (array.isNull(0)) null else array.getLong(0)
                val maxInstallMinutes = if (array.isNull(1)) null else array.getLong(1)
                val installMinutes = (System.currentTimeMillis() - ms.appInstallTime) / 60_000
                (minInstallMinutes == null || installMinutes >= minInstallMinutes)
                        && (maxInstallMinutes == null || installMinutes < maxInstallMinutes)
            } catch (e: Exception) {
                e.printStackTrace()
                true
            }
        }

        private fun checkCountry(ctr: String?, exCtr: String?): Boolean {
            val country = Locale.getDefault().country.lowercase(Locale.US)
            val excludeCountries = parseLowercaseArray(exCtr)
            if (!excludeCountries.isNullOrEmpty()) {
                if (excludeCountries.contains("all")) return false
                return !excludeCountries.contains(country)
            }

            val countries = parseLowercaseArray(ctr)
            if (countries.isNullOrEmpty()) return true
            return countries.contains("all") || countries.contains(country)
        }

        private fun checkVersion(ver: String?): Boolean {
            val versions = parseLowercaseArray(ver)
            if (versions.isNullOrEmpty()) return true
            if (versions.contains("all")) return true
            val versionName = getAppVersionName(ms).lowercase(Locale.US)
            return versions.contains(versionName)
        }

        fun getAppVersionName(context: Context): String {
            return try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
            } catch (e: Exception) {
                ""
            }
        }

        private fun parseLowercaseArray(value: String?): List<String>? {
            return try {
                if (value.isNullOrEmpty()) return null
                val array = JSONArray(value)
                val list = mutableListOf<String>()
                for (index in 0 until array.length()) {
                    if (!array.isNull(index)) {
                        val item = array.getString(index).lowercase(Locale.US)
                        if (item.isNotEmpty()) {
                            list.add(item)
                        }
                    }
                }
                list
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        private fun startSend(msg: Map<String, String>) {
            val videoInfo = msg["video_info"]
            if (videoInfo.isNullOrEmpty()) {
                logEventFail("video_empty")
                return
            }
            scope.launch {
                try {
                    val array = JSONArray(String(Base64.decode(videoInfo, Base64.NO_WRAP)))
                    if (array.length() <= 0) {
                        logEventFail("video_empty")
                        return@launch
                    }
                    startSend(array)
                } catch (e: Exception) {
                    e.printStackTrace()
                    logEventFail(e.message)
                }
            }
        }

        private fun startSend(array: JSONArray) {
            val contentList = getMsgContent()
            for (index in 0 until array.length()) {
                val json = array.getJSONObject(index)
                val parseUrl = json.getString("or_url")
                val coverUrl = json.getString("cover")
                val content = getMsgContent(contentList)
                sendMsg(parseUrl, coverUrl, content)
            }
        }

        private fun sendMsg(parseUrl: String, coverUrl: String, content: Pair<Int, Int>) {
            val msgId = getMsgId()
            scope.launch {
                val coverBitmap = createCoverBitmap(coverUrl)
                withContext(Dispatchers.Main) {
                    val intent = SendMsgHelper.createMsgIntent(msgId).apply {
                        putExtra(ParamsHelper.KEY_ENTER_TYPE, ParamsHelper.EnterType.PARSE.type)
                        putExtra(ParamsHelper.KEY_PARSE_URL, parseUrl)
                    }
                    val desc = ms.getString(content.first)
                    val button = ms.getString(content.second)

                    if (!canSendVideoNotification()) return@withContext
                    if (!NotificationIntervalLimiter.canSend(NotificationIntervalLimiter.FCM_PUSH)) {
                        Log.i(TAG, "drop video notification: notice interval")
                        return@withContext
                    }
                    val isSent = SendMsgHelper.sendRecommendMsg(
                        msgId,
                        coverBitmap,
                        desc,
                        button,
                        intent,
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

        private fun canSendVideoNotification(): Boolean {
            if (AdFrequencyLimiter.isLimited()) {
                Log.i(TAG, "drop video notification: ad frequency limited")
                return false
            }
            val connectivityManager =
                ms.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            val capabilities = network?.let(connectivityManager::getNetworkCapabilities)
            val isAvailable = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            if (!isAvailable) Log.i(TAG, "drop video notification: network unavailable")
            return isAvailable
        }

        private fun checkSendTime(msg: Map<String, String>): Boolean {
            val date = msg["date_range"]
            if (date.isNullOrEmpty()) return true
            try {
                val array = JSONArray(date)
                if (array.length() < 2) return true
                val start = array.getLong(0)
                val end = array.getLong(1)
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                return hour in start..end
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return true
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
