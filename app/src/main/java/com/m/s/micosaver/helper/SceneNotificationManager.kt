package com.m.s.micosaver.helper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.bumptech.glide.Glide
import com.m.s.micosaver.R
import com.m.s.micosaver.ad.AdFrequencyLimiter
import com.m.s.micosaver.channel.AppChannelHelper
import com.m.s.micosaver.db.info.RecommendBean
import com.m.s.micosaver.ex.scope
import com.m.s.micosaver.firebase.FirebaseHelper
import com.m.s.micosaver.ms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object SceneNotificationManager {
    const val TAG = "SceneNotification"

    private const val HOME_DELAY = 3_000L
    private const val BACKGROUND_DELAY = 10_000L
    private const val RECENT_APPS_DELAY = 5_000L
    private const val BACKGROUND_CLASSIFY_DELAY = 1_000L
    private const val SYSTEM_DIALOG_WINDOW = 2_000L

    enum class Scene(val logName: String) {
        HOME("home"),
        BACKGROUND("background"),
        UNLOCK("unlock"),
        USB_CONNECTED("usb_connected"),
        AD_CLICK("ad_click"),
        RECENT_APPS("recent_apps"),
        WIFI_CHANGED("wifi_changed"),
        APP_INSTALLED("app_installed"),
        BOOT("boot"),
        FILE_CHANGED("file_changed"),
        SCREENSHOT("screenshot"),
        POWER_CONNECTED("power_connected"),
    }

    private val backgroundClassifier = BackgroundSceneClassifier(SYSTEM_DIALOG_WINDOW)
    private val wifiNetworks = mutableSetOf<Network>()
    private var usbConnected: Boolean? = null

    fun init() {
        registerSystemReceiver()
        registerWifiCallback()
        FileChangeMonitor.start()
    }

    fun onAppBackgrounded() {
        val backgroundTime = SystemClock.elapsedRealtime()
        scope.launch {
            delay(BACKGROUND_CLASSIFY_DELAY)
            val now = SystemClock.elapsedRealtime()
            val (scene, markedAt) = backgroundClassifier.consume(now)
            val totalDelay = when (scene) {
                Scene.HOME -> HOME_DELAY
                Scene.RECENT_APPS -> RECENT_APPS_DELAY
                else -> BACKGROUND_DELAY
            }
            val startTime = markedAt ?: backgroundTime
            schedule(scene, (totalDelay - (now - startTime)).coerceAtLeast(0L))
        }
    }

    fun schedule(scene: Scene, delayMillis: Long = 0L) {
        Log.i(TAG, "scene=${scene.logName} scheduled delayMs=$delayMillis")
        scope.launch {
            if (delayMillis > 0L) delay(delayMillis)
            send(scene)
        }
    }

    private fun registerSystemReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction("android.hardware.usb.action.USB_STATE")
        }
        ContextCompat.registerReceiver(
            ms,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        Intent.ACTION_CLOSE_SYSTEM_DIALOGS -> {
                            when (intent.getStringExtra("reason")) {
                                "homekey" -> backgroundClassifier.mark(
                                    Scene.HOME,
                                    SystemClock.elapsedRealtime(),
                                )
                                "recentapps" -> backgroundClassifier.mark(
                                    Scene.RECENT_APPS,
                                    SystemClock.elapsedRealtime(),
                                )
                            }
                        }
                        Intent.ACTION_USER_PRESENT -> schedule(Scene.UNLOCK)
                        Intent.ACTION_POWER_CONNECTED -> schedule(Scene.POWER_CONNECTED)
                        "android.hardware.usb.action.USB_STATE" -> {
                            val connected = intent.getBooleanExtra("connected", false)
                            if (usbConnected == false && connected) schedule(Scene.USB_CONNECTED)
                            usbConnected = connected
                        }
                    }
                }
            },
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    private fun registerWifiCallback() {
        val manager = ms.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        manager.allNetworks.filterTo(wifiNetworks) { network ->
            manager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        manager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val shouldNotify = synchronized(wifiNetworks) {
                    val wasEmpty = wifiNetworks.isEmpty()
                    val added = wifiNetworks.add(network)
                    wasEmpty && added
                }
                if (shouldNotify) schedule(Scene.WIFI_CHANGED)
            }

            override fun onLost(network: Network) {
                val shouldNotify = synchronized(wifiNetworks) {
                    wifiNetworks.remove(network) && wifiNetworks.isEmpty()
                }
                if (shouldNotify) schedule(Scene.WIFI_CHANGED)
            }
        })
    }

    private fun send(scene: Scene) {
        val blockedReason = blockedReason(scene)
        if (blockedReason != null) {
            Log.i(TAG, "scene=${scene.logName} sent=false reason=$blockedReason")
            return
        }
        RecommendManager.getPurchaseUserFunList { list ->
            val recommend = list.randomOrNull()
            if (recommend == null) {
                Log.i(TAG, "scene=${scene.logName} sent=false reason=no_recommendation")
                return@getPurchaseUserFunList
            }
            scope.launch {
                val image = createCoverBitmap(recommend.cover)
                withContext(Dispatchers.Main) {
                    val currentBlockedReason = blockedReason(scene)
                    if (currentBlockedReason != null) {
                        Log.i(TAG, "scene=${scene.logName} sent=false reason=$currentBlockedReason")
                        return@withContext
                    }
                    val sent = sendRecommendation(recommend, image)
                    Log.i(TAG, "scene=${scene.logName} sent=$sent")
                    if (sent) {
                        NotificationIntervalLimiter.recordSent(scene.logName)
                        FirebaseHelper.logEvent("ms_send_msg_suc", Bundle().apply {
                            putString("type", scene.logName)
                        })
                    }
                }
            }
        }
    }

    private fun blockedReason(scene: Scene): String? {
        if (!AppChannelHelper.isPro) return "non_purchase_user"
        if (AdFrequencyLimiter.isLimited()) return "ad_frequency_limited"
        val manager = ms.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = manager.activeNetwork?.let(manager::getNetworkCapabilities)
        val connected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        if (!connected) return "network_unavailable"
        return if (NotificationIntervalLimiter.canSend(scene.logName)) {
            null
        } else {
            "notice_interval"
        }
    }

    private fun sendRecommendation(recommend: RecommendBean, image: Bitmap?): Boolean {
        val msgId = SendMsgHelper.getMsgId()
        val intent = SendMsgHelper.createMsgIntent(msgId).apply {
            putExtra(ParamsHelper.KEY_ENTER_TYPE, ParamsHelper.EnterType.PARSE.type)
            putExtra(ParamsHelper.KEY_PARSE_URL, recommend.url)
        }
        val title = recommend.desc.ifBlank {
            recommend.authorName.ifBlank { ms.getString(R.string.ms_app_name) }
        }
        return SendMsgHelper.sendRecommendMsg(
            msgId,
            image,
            title,
            ms.getString(R.string.ms_view),
            intent,
        )
    }

    private fun createCoverBitmap(coverUrl: String): Bitmap? {
        if (coverUrl.isBlank()) return null
        return try {
            val drawable = Glide.with(ms).asDrawable().load(coverUrl).submit().get() ?: return null
            if (drawable is BitmapDrawable) {
                drawable.bitmap
            } else {
                createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(1),
                    drawable.intrinsicHeight.coerceAtLeast(1),
                ).also { bitmap ->
                    val canvas = Canvas(bitmap)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                }
            }
        } catch (e: Exception) {
            Log.i(TAG, "load recommendation cover failed", e)
            null
        }
    }
}

internal class BackgroundSceneClassifier(private val windowMillis: Long) {
    private var homeAt = 0L
    private var recentAppsAt = 0L

    @Synchronized
    fun mark(scene: SceneNotificationManager.Scene, time: Long) {
        when (scene) {
            SceneNotificationManager.Scene.HOME -> homeAt = time
            SceneNotificationManager.Scene.RECENT_APPS -> recentAppsAt = time
            else -> Unit
        }
    }

    @Synchronized
    fun consume(now: Long): Pair<SceneNotificationManager.Scene, Long?> {
        val homeTime = homeAt.takeIf { now - it in 0..windowMillis }
        val recentTime = recentAppsAt.takeIf { now - it in 0..windowMillis }
        homeAt = 0L
        recentAppsAt = 0L
        return when {
            homeTime != null && (recentTime == null || homeTime >= recentTime) ->
                SceneNotificationManager.Scene.HOME to homeTime
            recentTime != null -> SceneNotificationManager.Scene.RECENT_APPS to recentTime
            else -> SceneNotificationManager.Scene.BACKGROUND to null
        }
    }
}
