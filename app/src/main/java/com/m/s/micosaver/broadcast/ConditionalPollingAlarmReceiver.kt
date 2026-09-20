package com.m.s.micosaver.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.m.s.micosaver.ex.scope
import com.m.s.micosaver.helper.ConditionalPollingManager
import kotlinx.coroutines.launch

class ConditionalPollingAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val source = intent?.action ?: return
        if (source != ConditionalPollingManager.SOURCE_ALARM_60 &&
            source != ConditionalPollingManager.SOURCE_ALARM_IDLE
        ) {
            return
        }
        val pendingResult = goAsync()
        scope.launch {
            try {
                ConditionalPollingManager.onAlarmTriggered(source)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
