package com.m.s.micosaver.utils

import android.content.Intent
import android.os.Build
import com.m.s.micosaver.ms
import com.m.s.micosaver.services.PermanentNoticeService

object Tools {

    /**
     * 三星 Android 12/12L 对应 One UI 4.x，仅该范围启用厂商专用布局。
     */
    fun isSamsungOneUi4(): Boolean =
        Build.MANUFACTURER.equals("samsung", ignoreCase = true) &&
                Build.VERSION.SDK_INT in Build.VERSION_CODES.S..Build.VERSION_CODES.S_V2

    fun startForegroundService() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(ms, PermanentNoticeService::class.java).apply {
                    action = PermanentNoticeService.ACTION_START
                }
                ms.startForegroundService(intent)
            } else {
                val intent = Intent(ms, PermanentNoticeService::class.java).apply {
                    action = PermanentNoticeService.ACTION_START
                }
                ms.startService(intent)
            }
        }catch (e: Exception){
            e.printStackTrace()
        }
    }
}