package com.m.s.micosaver.helper

import android.util.Log
import com.m.s.micosaver.firebase.FirebaseHelper
import com.m.s.micosaver.ms

object NotificationIntervalLimiter {
    const val TAG = "NoticeInterval"
    const val FCM_PUSH = "fcm_push"

    @Synchronized
    fun canSend(scene: String, now: Long = System.currentTimeMillis()): Boolean {
        val config = resolveNoticeIntervalConfig(
            FirebaseHelper.remoteConfig.getNoticeIntervalConfig(),
            scene,
        )
        val installedSeconds = (now - ms.appInstallTime).coerceAtLeast(0L) / 1_000L
        if (!isNoticeInstallTimeAllowed(config, installedSeconds)) {
            Log.i(
                TAG,
                "scene=$scene allowed=false reason=install_time " +
                    "installedSeconds=$installedSeconds installTime=${config.installTimeSeconds}",
            )
            return false
        }
        val intervalSeconds = resolveNoticeIntervalSeconds(
            config,
            installedSeconds,
        )
        val lastSentTime = ms.data.getNoticeLastSentTime(scene)
        val allowed = isNoticeIntervalAllowed(lastSentTime, now, intervalSeconds)
        if (!allowed) {
            val remainingSeconds = (
                intervalSeconds * 1_000L - (now - lastSentTime)
            ).coerceAtLeast(0L) / 1_000L
            Log.i(
                TAG,
                "scene=$scene allowed=false intervalSeconds=$intervalSeconds " +
                    "remainingSeconds=$remainingSeconds",
            )
        }
        return allowed
    }

    @Synchronized
    fun recordSent(scene: String, now: Long = System.currentTimeMillis()) {
        ms.data.setNoticeLastSentTime(scene, now)
        Log.i(TAG, "scene=$scene sent=true recordedAt=$now")
    }
}

internal const val DEFAULT_NOTICE_INTERVAL_SECONDS = 300L

internal data class NoticeIntervalConfig(
    val installTimeSeconds: Long = 0L,
    val defaultIntervalSeconds: Long = DEFAULT_NOTICE_INTERVAL_SECONDS,
    val installRange: NoticeInstallRange? = null,
    val installOther: NoticeInstallOther? = null,
)

internal data class NoticeInstallRange(
    val startTimeSeconds: Long,
    val endTimeSeconds: Long,
    val intervalSeconds: Long,
)

internal data class NoticeInstallOther(
    val timeSeconds: Long,
    val intervalSeconds: Long,
)

internal fun resolveNoticeIntervalConfig(
    config: Map<String, NoticeIntervalConfig>?,
    scene: String,
): NoticeIntervalConfig = config?.get(scene) ?: NoticeIntervalConfig()

internal fun isNoticeInstallTimeAllowed(
    config: NoticeIntervalConfig,
    installedSeconds: Long,
): Boolean = config.installTimeSeconds <= 0L || installedSeconds > config.installTimeSeconds

internal fun resolveNoticeIntervalSeconds(
    config: NoticeIntervalConfig,
    installedSeconds: Long,
): Long {
    config.installRange?.let { range ->
        if (installedSeconds >= range.startTimeSeconds && installedSeconds < range.endTimeSeconds) {
            return range.intervalSeconds
        }
    }
    config.installOther?.let { other ->
        if (installedSeconds > other.timeSeconds) return other.intervalSeconds
    }
    return config.defaultIntervalSeconds
}

internal fun isNoticeIntervalAllowed(
    lastSentTime: Long,
    now: Long,
    intervalSeconds: Long,
): Boolean {
    if (intervalSeconds <= 0L || lastSentTime <= 0L || now < lastSentTime) return true
    val intervalMillis = intervalSeconds
        .coerceAtMost(Long.MAX_VALUE / 1_000L) * 1_000L
    return now - lastSentTime >= intervalMillis
}
