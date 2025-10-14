package com.m.s.micosaver.ui.dialog

import android.view.LayoutInflater
import android.view.View
import com.m.s.micosaver.databinding.MsDialogPermissionBinding
import com.m.s.micosaver.ui.base.BaseActivity

class PermissionDialog(activity: BaseActivity, private val allow: () -> Unit) : BaseDialog2(activity, false) {
    private val binding by lazy { MsDialogPermissionBinding.inflate(LayoutInflater.from(context)) }

    override fun onRootView(): View {
        return binding.root
    }

    override fun onInitView() {
        binding.run {
            dialogCloseIv.setOnClickListener {
                dismiss()
            }

            allowBtn.setOnClickListener {
                dismiss()
                allow.invoke()
            }
        }
    }
}