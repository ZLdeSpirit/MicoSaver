package com.m.s.micosaver.ui.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import com.m.s.micosaver.R

abstract class BaseDialog2 (context: Context, private val canCancel: Boolean = true) :
    Dialog(context, R.style.MS_DialogTheme2) {

    abstract fun onRootView(): View

    abstract fun onInitView()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCancelable(canCancel)
        setCanceledOnTouchOutside(canCancel)
        setContentView(onRootView())
        onInitView()
    }

}