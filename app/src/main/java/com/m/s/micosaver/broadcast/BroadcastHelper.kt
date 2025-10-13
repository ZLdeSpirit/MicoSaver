package com.m.s.micosaver.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.m.s.micosaver.ms

object BroadcastHelper {

    const val ACTION_SWITCH_LANGUAGE = "com.vid.save.drop.broadcast.ACTION_SWITCH_LANGUAGE"
    const val ACTION_SAVING_VIDEO_CHANGE =
        "com.vid.save.drop.broadcast.ACTION_SAVING_VIDEO_CHANGE"
    const val ACTION_SAVED_VIDEO_CHANGE =
        "com.vid.save.drop.broadcast.ACTION_SAVED_VIDEO_CHANGE"
    const val ACTION_PLAY_VIDEO_CHANGE = "com.vid.save.drop.broadcast.ACTION_PLAY_VIDEO_CHANGE"

    fun register(
        context: Context,
        intentFilter: IntentFilter,
        receiverCallback: (Intent?) -> Unit
    ): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                receiverCallback(intent)
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            intentFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        return receiver
    }

    fun sendBroadcast(action: String) {
        sendBroadcast(Intent(action))
    }

    fun sendBroadcast(intent: Intent) {
        intent.`package` = ms.packageName
        ms.sendBroadcast(intent)
    }

}