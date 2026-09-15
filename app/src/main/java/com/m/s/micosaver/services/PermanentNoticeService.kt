package com.m.s.micosaver.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.m.s.micosaver.R
import com.m.s.micosaver.channel.AppChannelHelper
import com.m.s.micosaver.ms
import com.m.s.micosaver.ui.activity.MsSplashActivity

class PermanentNoticeService : Service() {
    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val PERMANENT_NOTIFICATION_ID = 34522
        const val NOTIFICATION_CHANNEL_ID = "ForegroundChannel"
        const val TYPE_RECOMMEND = 0
        const val TYPE_FUNC = 1
        const val TYPE_LINK = 2
    }

    var permanentType = 0
    private var requestCode = 135425

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startForegroundService()
            ACTION_STOP -> stopForegroundService()
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        val type = permanentType % 3
        val notification = createPermanentNotification(type)
        if(AppChannelHelper.isPro) {
            ++permanentType
        }
        // 启动前台服务
        startForeground(PERMANENT_NOTIFICATION_ID, notification)
    }

    private fun stopForegroundService() {
        stopForeground(true)
        stopSelf()
    }

    fun createPermanentNotification(type: Int): Notification{
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "permanent_notice_channel"
            val importance = NotificationManager.IMPORTANCE_DEFAULT // 默认重要性，减少打扰
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, importance).apply {
                setSound(null, null) // 静音
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC // 锁屏可见性
            }
            NotificationManagerCompat.from(ms).createNotificationChannel(channel)
        }

        // 创建点击通知的Intent
        val intent = Intent(ms, MsSplashActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage(ms.packageName)
        }

        val pendingIntent = makePendingIntent(intent)
        val remoteViews = createPermanentRemoteViews(type)
        remoteViews.setOnClickPendingIntent(R.id.notificationContainer, pendingIntent)
        // 构建通知
        val notification = NotificationCompat.Builder(ms, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ms_ic_launcher_foreground) // 必须使用透明背景的图标
            .setContentIntent(pendingIntent) // 点击通知的跳转
            .setPriority(NotificationCompat.PRIORITY_DEFAULT) // 默认优先级
            .setOngoing(true) // 设置为常驻通知
            .setAutoCancel(false) // 禁止自动取消
            .setOnlyAlertOnce(true) // 仅首次提醒
            .setCustomBigContentView(remoteViews)
            .setCustomHeadsUpContentView(remoteViews)
            .setContent(remoteViews)
            .setCustomContentView(remoteViews)
            .build()
        return notification
    }

    fun makePendingIntent(intent: Intent): PendingIntent {
        val pendingIntent = PendingIntent.getActivity(
            ms, requestCode++, intent, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )
        return pendingIntent
    }

    private fun createPermanentRemoteViews(type: Int): RemoteViews {
        return RemoteViews(ms.packageName, when(type){
            TYPE_RECOMMEND -> R.layout.ms_notification_p_1
            TYPE_FUNC -> R.layout.ms_notification_p_2
            TYPE_LINK -> R.layout.ms_notification_p_3
            else -> R.layout.ms_notification_p_1
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(true)
    }
}