package com.m.s.micosaver.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.m.s.micosaver.helper.ParamsHelper
import com.m.s.micosaver.helper.SendMsgHelper

class NotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        SendMsgHelper.cancelCircleNotice(
            intent?.getIntExtra(ParamsHelper.KEY_MSG_ID, -1) ?: -1,
            "dismissed",
        )
    }
}
