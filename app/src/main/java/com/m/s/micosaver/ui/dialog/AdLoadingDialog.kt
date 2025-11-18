package com.m.s.micosaver.ui.dialog

import android.view.LayoutInflater
import android.view.View
import com.m.s.micosaver.databinding.MsDialogAdLoadingBinding
import com.m.s.micosaver.ui.base.BaseActivity

class AdLoadingDialog(activity: BaseActivity) : BaseDialog2(activity, false) {
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
}