package com.m.s.micosaver.ex

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics
import com.m.s.micosaver.R
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

fun Context.toast(msg: String = "", duration: Int = Toast.LENGTH_SHORT, gravity: Int = Gravity.CENTER) {
    val inflater = LayoutInflater.from(this)
    val view = inflater.inflate(R.layout.ms_custom_toast, null)
    val toastTv = view.findViewById<TextView>(R.id.toastTv)
    toastTv.text = msg.ifEmpty { getString(R.string.ms_delete_successfully) }
    Toast(this).apply {
        setView(view)
        setDuration(duration)
        setGravity(gravity, 0, 0)
        show()
    }
}