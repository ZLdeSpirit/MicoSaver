package com.m.s.micosaver.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.m.s.micosaver.helper.SceneNotificationManager

class SceneEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED ->
                SceneNotificationManager.schedule(SceneNotificationManager.Scene.BOOT)
            Intent.ACTION_PACKAGE_ADDED -> {
                if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    SceneNotificationManager.schedule(SceneNotificationManager.Scene.APP_INSTALLED)
                }
            }
        }
    }
}
