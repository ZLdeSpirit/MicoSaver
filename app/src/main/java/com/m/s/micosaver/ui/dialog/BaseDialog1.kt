package com.m.s.micosaver.ui.dialog

import android.content.Context
import android.os.Bundle
import android.view.View
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.m.s.micosaver.R

abstract class BaseDialog1(context: Context, private val canCancel: Boolean = true) :
    BottomSheetDialog(context, R.style.MS_DialogTheme1) {

    abstract fun onRootView(): View

    abstract fun onInitView()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCancelable(canCancel)
        setCanceledOnTouchOutside(canCancel)
        setContentView(onRootView())
        onInitView()
    }

    override fun onStart() {
        super.onStart()
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

}