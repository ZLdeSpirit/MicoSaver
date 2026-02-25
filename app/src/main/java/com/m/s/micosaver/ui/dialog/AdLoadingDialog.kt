package com.m.s.micosaver.ui.dialog

import android.view.LayoutInflater
import android.view.View
import com.m.s.micosaver.databinding.MsDialogAdLoadingBinding
import com.m.s.micosaver.ui.base.BaseActivity

class AdLoadingDialog(private val activity: BaseActivity) : BaseDialog2(activity, false) {
    private val binding by lazy { MsDialogAdLoadingBinding.inflate(LayoutInflater.from(context)) }

    override fun onRootView(): View {
        return binding.root
    }

    override fun onInitView() {
    }

    fun showDialog(): AdLoadingDialog {
        show()
        return this
    }

    override fun show() {
        if (!activity.isFinishing &&!activity.isDestroyed && !isShowing) {
            try {
                super.show()
            }catch (e: Exception){
                e.printStackTrace()
            }
        }
    }

    override fun dismiss() {
        if (!activity.isFinishing && !activity.isDestroyed && isShowing) {
            try {
                super.dismiss()
            }catch (e: Exception){
                e.printStackTrace()
            }
        }
    }
}