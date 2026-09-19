package com.m.s.micosaver.helper

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.m.s.micosaver.R
import com.m.s.micosaver.channel.AppChannelHelper
import com.m.s.micosaver.ex.scope
import com.m.s.micosaver.firebase.FirebaseHelper
import com.m.s.micosaver.ms
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.random.Random

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
internal object MediaNoticeManager {
    const val TAG = "MediaNotice"
    const val NOTIFICATION_ID = 89492

    private const val CHANNEL_ID = "ms_media_heads_up_v1"
    private const val SESSION_ID = "ms_media_notice"
    private const val MEDIA_INTERVAL_MILLIS = 2_000L
    private const val MIN_RESET_MINUTES = 20
    private const val MAX_RESET_MINUTES = 40

    private val resetPolicy = MediaNoticeResetPolicy {
        Random.nextInt(MIN_RESET_MINUTES, MAX_RESET_MINUTES + 1)
    }
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var circleTask: MediaCircleTask? = null
    private var currentCircle = 0
    private var totalCircle = 0
    private var isNoticeShown = false

    @Synchronized
    fun send(
        title: String,
        action: String,
        contentIntent: Intent,
        ordinaryConfig: CircleNoticeConfig,
    ): Boolean {
        ensureChannel()
        val now = SystemClock.elapsedRealtime()
        if (resetPolicy.shouldReset(now)) {
            NotificationManagerCompat.from(ms).cancel(NOTIFICATION_ID)
            isNoticeShown = false
            resetPolicy.recordCancellation(now)
            Log.i(
                TAG,
                "reset=true nextResetMinutes=${resetPolicy.currentIntervalMillis / 60_000L}",
            )
        }

        val previousTask = circleTask
        val newTotalCircle = resolveMediaCircleCount(ordinaryConfig)
        val sent = notify(title, action, contentIntent, silent = false)
        Log.i(TAG, "id=$NOTIFICATION_ID current=1 total=$newTotalCircle silent=false sent=$sent")
        if (!sent) return false
        previousTask?.job?.cancel()
        totalCircle = newTotalCircle
        currentCircle = 1
        circleTask = null
        if (newTotalCircle > 1) {
            val task = MediaCircleTask(current = 1, total = newTotalCircle)
            task.job = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    for (current in 2..task.total) {
                        delay(MEDIA_INTERVAL_MILLIS)
                        val updateSent = withContext(Dispatchers.Main) {
                            if (!canSendMediaNotice()) {
                                cancelActiveIfExists("condition_changed")
                                return@withContext false
                            }
                            task.current = current
                            currentCircle = current
                            notify(title, action, contentIntent, silent = true)
                        }
                        Log.i(
                            TAG,
                            "id=$NOTIFICATION_ID current=$current total=${task.total} " +
                                "silent=true sent=$updateSent",
                        )
                        if (!updateSent) break
                    }
                } finally {
                    synchronized(this@MediaNoticeManager) {
                        if (circleTask === task) circleTask = null
                    }
                }
            }
            circleTask = task
            task.job.start()
        }
        return true
    }

    @Synchronized
    fun cancelIfMediaNotice(notificationId: Int, reason: String): Boolean {
        if (notificationId != NOTIFICATION_ID) return false
        cancelActive(reason)
        return true
    }

    @Synchronized
    fun cancelActiveIfExists(reason: String) {
        if (!isNoticeShown && circleTask == null && mediaSession == null) return
        cancelActive(reason)
    }

    @SuppressLint("MissingPermission")
    private fun notify(
        title: String,
        action: String,
        contentIntent: Intent,
        silent: Boolean,
    ): Boolean {
        return try {
            val session = getOrCreateSession(title, action)
            val pendingIntent = PendingIntent.getActivity(
                ms,
                NOTIFICATION_ID,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val builder = NotificationCompat.Builder(ms, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ms_ic_launcher)
                .setContentTitle(title)
                .setContentText(action)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setStyle(MediaStyleNotificationHelper.MediaStyle(session))
            if (silent) {
                builder.setSilent(true)
                builder.setSound(null)
                builder.setVibrate(null)
            } else {
                builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                builder.setVibrate(longArrayOf(0, 1000))
            }
            NotificationManagerCompat.from(ms).notify(NOTIFICATION_ID, builder.build())
            isNoticeShown = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "send failed", e)
            false
        }
    }

    private fun getOrCreateSession(title: String, action: String): MediaSession {
        val item = MediaItem.Builder()
            .setUri(
                Uri.parse("android.resource://${ms.packageName}/${R.raw.media_session_silence}")
            )
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(action)
                    .build(),
            )
            .build()
        mediaSession?.let { session ->
            player?.apply {
                setMediaItem(item)
                playWhenReady = false
                prepare()
            }
            return session
        }
        val newPlayer = ExoPlayer.Builder(ms).build().apply {
            setMediaItem(item)
            playWhenReady = false
            prepare()
        }
        player = newPlayer
        return MediaSession.Builder(ms, MediaNoticePlayer(newPlayer))
            .setId(SESSION_ID)
            .setMediaButtonPreferences(emptyList())
            .build()
            .also { mediaSession = it }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        NotificationManagerCompat.from(ms).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                ms.getString(R.string.ms_app_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000)
            },
        )
    }

    private fun canSendMediaNotice(): Boolean {
        return !ms.isOpenMsg && AppChannelHelper.isPro &&
            FirebaseHelper.remoteConfig.getMediaNoticeSwitch()
    }

    private fun cancelActive(reason: String) {
        circleTask?.job?.cancel()
        circleTask = null
        NotificationManagerCompat.from(ms).cancel(NOTIFICATION_ID)
        isNoticeShown = false
        resetPolicy.recordCancellation(SystemClock.elapsedRealtime())
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        Log.i(
            TAG,
            "id=$NOTIFICATION_ID stopped reason=$reason current=$currentCircle total=$totalCircle",
        )
    }

    private class MediaCircleTask(
        @Volatile var current: Int,
        val total: Int,
    ) {
        lateinit var job: Job
    }
}

internal fun resolveMediaCircleCount(config: CircleNoticeConfig): Int {
    val totalMillis = config.circleCount.toLong() * config.intervalMillis
    return ceil(totalMillis.toDouble() / 2_000.0)
        .toLong()
        .coerceAtLeast(1L)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
}

internal class MediaNoticeResetPolicy(
    private val randomMinutes: () -> Int,
) {
    var lastCancellationTime: Long = 0L
        private set
    var currentIntervalMillis: Long = 0L
        private set

    fun shouldReset(now: Long): Boolean {
        if (lastCancellationTime <= 0L || currentIntervalMillis <= 0L) return true
        return now >= lastCancellationTime &&
            now - lastCancellationTime >= currentIntervalMillis
    }

    fun recordCancellation(now: Long) {
        lastCancellationTime = now
        currentIntervalMillis = randomMinutes().coerceIn(20, 40) * 60_000L
    }
}
