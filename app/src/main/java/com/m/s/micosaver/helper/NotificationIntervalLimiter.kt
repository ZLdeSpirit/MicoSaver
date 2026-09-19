package com.m.s.micosaver.helper

import android.util.Log
import com.m.s.micosaver.firebase.FirebaseHelper
import com.m.s.micosaver.ms

object NotificationIntervalLimiter {
    const val TAG = "NoticeInterval"
    const val FCM_PUSH = "fcm_push"

    @Synchronized
    fun canSend(scene: String, now: Long = System.currentTimeMillis()): Boolean {
        val intervalSeconds = resolveNoticeIntervalSeconds(
            FirebaseHelper.remoteConfig.getNoticeIntervalConfig(),
            scene,
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

internal fun resolveNoticeIntervalSeconds(
    config: Map<String, Long>?,
    scene: String,
): Long = config?.get(scene) ?: DEFAULT_NOTICE_INTERVAL_SECONDS

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
