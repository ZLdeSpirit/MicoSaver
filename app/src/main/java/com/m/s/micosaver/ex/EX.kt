package com.m.s.micosaver.ex

import android.content.Context
import android.widget.Toast
import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

val scope by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
        Firebase.crashlytics.recordException(throwable)
    })
}

fun Context.toast(msg: String) {
    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}