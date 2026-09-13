package com.m.s.micosaver.utils

import android.os.Build

object Tools {

    /**
     * 三星 Android 12/12L 对应 One UI 4.x，仅该范围启用厂商专用布局。
     */
    fun isSamsungOneUi4(): Boolean =
        Build.MANUFACTURER.equals("samsung", ignoreCase = true) &&
                Build.VERSION.SDK_INT in Build.VERSION_CODES.S..Build.VERSION_CODES.S_V2
}