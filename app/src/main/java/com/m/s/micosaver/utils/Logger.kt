package com.m.s.micosaver.utils

import android.util.Log
import com.m.s.micosaver.BuildConfig

object Logger {

    fun logDebugI(tag: String, msg: String){
        if (BuildConfig.DEBUG){
            Log.i(tag, msg)
        }
    }
}