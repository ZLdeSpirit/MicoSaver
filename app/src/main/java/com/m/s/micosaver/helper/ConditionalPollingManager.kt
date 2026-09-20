package com.m.s.micosaver.helper

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.m.s.micosaver.broadcast.ConditionalPollingAlarmReceiver
import com.m.s.micosaver.ex.scope
import com.m.s.micosaver.ms
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ConditionalPollingManager {
    const val TAG = "ConditionPolling"

    const val SOURCE_WORK_IMMEDIATE = "work_immediate"
    const val SOURCE_WORK_PERIODIC = "work_periodic_15m"
    const val SOURCE_WORK_CHAIN = "work_chain_60s"
    const val SOURCE_ALARM_60 = "alarm_60s"
    const val SOURCE_ALARM_IDLE = "alarm_idle_600s"
    const val SOURCE_TIME_TICK = "time_tick"
    const val SOURCE_PROCESS_LOOP = "process_loop_60s"

    private const val INPUT_SOURCE = "polling_source"
    private const val WORK_IMMEDIATE = "condition_polling_immediate"
    private const val WORK_PERIODIC = "condition_polling_periodic"
    private const val WORK_CHAIN = "condition_polling_chain"
    private const val PROCESS_INTERVAL_MILLIS = 60_000L
    private const val ALARM_INTERVAL_MILLIS = 60_000L
    private const val IDLE_ALARM_INTERVAL_MILLIS = 600_000L

    private var timeTickRegistered = false
    private var alarm60Scheduled = false
    private var idleAlarmScheduled = false
    private var processLoopJob: Job? = null
    private val directFcmGate = DirectFcmGate()
    private val minuteTickGate = MinuteTickGate()

    fun start(trigger: String, enqueueImmediate: Boolean = true) {
        Log.i(TAG, "start trigger=$trigger immediate=$enqueueImmediate")
        registerTimeTickReceiver()
        enqueuePeriodicWork()
        enqueueWorkChain()
        scheduleAlarm(SOURCE_ALARM_60)
        scheduleAlarm(SOURCE_ALARM_IDLE)
        startProcessLoop()
        if (enqueueImmediate) {
            enqueueImmediate(trigger)
        } else {
            WorkManager.getInstance(ms).cancelUniqueWork(WORK_IMMEDIATE)
        }
    }

    fun beginDirectFcmHandling() {
        val count = directFcmGate.begin()
        Log.i(TAG, "direct fcm begin count=$count")
    }

    fun finishDirectFcmHandling(trigger: String) {
        val count = directFcmGate.finish()
        Log.i(TAG, "direct fcm finish count=$count")
        if (count == 0) enqueueImmediate(trigger)
    }

    fun saveFcmMessage(data: Map<String, String>) {
        ms.data.lastFcmMessage = JSONObject(data).toString()
        Log.i(TAG, "saved fcm message type=${data["msg_type"] ?: "0"}")
    }

    fun enqueueImmediate(trigger: String) {
        val request = OneTimeWorkRequestBuilder<ConditionalImmediateWorker>()
            .setInputData(sourceData("$SOURCE_WORK_IMMEDIATE:$trigger"))
            .build()
        WorkManager.getInstance(ms).enqueueUniqueWork(
            WORK_IMMEDIATE,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    internal suspend fun execute(source: String) {
        Log.i(TAG, "source=$source execute=true")
        if (directFcmGate.isActive) {
            Log.i(TAG, "source=$source sent=false reason=direct_fcm_in_progress")
            return
        }
        val message = getSavedFcmMessage()
        if (message == null) {
            Log.i(TAG, "source=$source sent=false reason=no_saved_fcm")
            return
        }
        val blockedReason = FcmNotificationConditions.blockedReason(message)
        if (blockedReason != null) {
            Log.i(TAG, "source=$source sent=false reason=$blockedReason")
            return
        }
        val sent = RecommendationNotificationSender.send(
            logType = NotificationIntervalLimiter.FCM_PUSH,
            intervalScene = NotificationIntervalLimiter.FCM_PUSH,
            finalCheck = {
                val latestMessage = getSavedFcmMessage()
                !directFcmGate.isActive && latestMessage != null &&
                    FcmNotificationConditions.blockedReason(latestMessage) == null
            },
        )
        Log.i(TAG, "source=$source sent=$sent")
    }

    internal suspend fun onAlarmTriggered(source: String) {
        scheduleAlarm(source, replaceExisting = true)
        execute(source)
    }

    internal fun enqueueNextWorkChain() {
        val request = OneTimeWorkRequestBuilder<ConditionalChainWorker>()
            .setInitialDelay(60, TimeUnit.SECONDS)
            .setInputData(sourceData(SOURCE_WORK_CHAIN))
            .build()
        WorkManager.getInstance(ms).enqueueUniqueWork(
            WORK_CHAIN,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    private fun getSavedFcmMessage(): Map<String, String>? {
        val value = ms.data.lastFcmMessage ?: return null
        return try {
            val json = JSONObject(value)
            buildMap {
                json.keys().forEach { key -> put(key, json.getString(key)) }
            }
        } catch (e: Exception) {
            Log.i(TAG, "read saved fcm failed", e)
            null
        }
    }

    private fun enqueuePeriodicWork() {
        val request = PeriodicWorkRequestBuilder<ConditionalPeriodicWorker>(
            15,
            TimeUnit.MINUTES,
            15,
            TimeUnit.MINUTES,
        ).setInputData(sourceData(SOURCE_WORK_PERIODIC)).build()
        WorkManager.getInstance(ms).enqueueUniquePeriodicWork(
            WORK_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun enqueueWorkChain() {
        val request = OneTimeWorkRequestBuilder<ConditionalChainWorker>()
            .setInitialDelay(60, TimeUnit.SECONDS)
            .setInputData(sourceData(SOURCE_WORK_CHAIN))
            .build()
        WorkManager.getInstance(ms).enqueueUniqueWork(
            WORK_CHAIN,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    @Synchronized
    private fun scheduleAlarm(source: String, replaceExisting: Boolean = false) {
        val isIdleAlarm = source == SOURCE_ALARM_IDLE
        val isScheduled = if (isIdleAlarm) idleAlarmScheduled else alarm60Scheduled
        if (isScheduled && !replaceExisting) {
            Log.i(TAG, "source=$source schedule_kept=true")
            return
        }
        val delayMillis = if (isIdleAlarm) IDLE_ALARM_INTERVAL_MILLIS else ALARM_INTERVAL_MILLIS
        val requestCode = if (isIdleAlarm) 91_602 else 91_601
        val pendingIntent = PendingIntent.getBroadcast(
            ms,
            requestCode,
            Intent(ms, ConditionalPollingAlarmReceiver::class.java).apply {
                action = source
                putExtra(INPUT_SOURCE, source)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarmManager = ms.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = SystemClock.elapsedRealtime() + delayMillis
        if (isIdleAlarm) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                pendingIntent,
            )
        } else {
            alarmManager.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                pendingIntent,
            )
        }
        if (isIdleAlarm) {
            idleAlarmScheduled = true
        } else {
            alarm60Scheduled = true
        }
        Log.i(TAG, "source=$source scheduled delayMs=$delayMillis")
    }

    private fun registerTimeTickReceiver() {
        if (timeTickRegistered) return
        ContextCompat.registerReceiver(
            ms,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_TIME_TICK) {
                        if (!minuteTickGate.shouldRun(System.currentTimeMillis())) {
                            Log.i(TAG, "source=$SOURCE_TIME_TICK execute=false reason=duplicate_minute")
                            return
                        }
                        scope.launch { execute(SOURCE_TIME_TICK) }
                    }
                }
            },
            IntentFilter(Intent.ACTION_TIME_TICK),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        timeTickRegistered = true
    }

    private fun startProcessLoop() {
        if (processLoopJob?.isActive == true) return
        processLoopJob = scope.launch {
            while (isActive) {
                delay(PROCESS_INTERVAL_MILLIS)
                execute(SOURCE_PROCESS_LOOP)
            }
        }
    }

    private fun sourceData(source: String): Data = workDataOf(INPUT_SOURCE to source)

    internal fun sourceFrom(data: Data, fallback: String): String =
        data.getString(INPUT_SOURCE) ?: fallback
}

internal class MinuteTickGate {
    private var lastMinute = Long.MIN_VALUE

    @Synchronized
    fun shouldRun(nowMillis: Long): Boolean {
        val minute = nowMillis / 60_000L
        if (minute == lastMinute) return false
        lastMinute = minute
        return true
    }
}

internal class DirectFcmGate {
    private var activeCount = 0

    val isActive: Boolean
        @Synchronized get() = activeCount > 0

    @Synchronized
    fun begin(): Int {
        activeCount += 1
        return activeCount
    }

    @Synchronized
    fun finish(): Int {
        activeCount = (activeCount - 1).coerceAtLeast(0)
        return activeCount
    }
}

class ConditionalImmediateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ConditionalPollingManager.execute(
            ConditionalPollingManager.sourceFrom(
                inputData,
                ConditionalPollingManager.SOURCE_WORK_IMMEDIATE,
            ),
        )
        return Result.success()
    }
}

class ConditionalPeriodicWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ConditionalPollingManager.execute(
            ConditionalPollingManager.sourceFrom(
                inputData,
                ConditionalPollingManager.SOURCE_WORK_PERIODIC,
            ),
        )
        return Result.success()
    }
}

class ConditionalChainWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        try {
            ConditionalPollingManager.execute(
                ConditionalPollingManager.sourceFrom(
                    inputData,
                    ConditionalPollingManager.SOURCE_WORK_CHAIN,
                ),
            )
        } finally {
            ConditionalPollingManager.enqueueNextWorkChain()
        }
        return Result.success()
    }
}
