package com.m.s.micosaver.ui.dialog

import android.view.LayoutInflater
import android.view.View
import com.m.s.micosaver.databinding.MsDialogConnectVpnBinding
import com.m.s.micosaver.ui.base.BaseActivity
import kotlin.system.exitProcess

class ConnectVpnDialog(val activity: BaseActivity) : BaseDialog2(activity, false) {
    private val binding by lazy { MsDialogConnectVpnBinding.inflate(LayoutInflater.from(context)) }

    override fun onRootView(): View {
        return binding.root
    }

    override fun onInitView() {
        binding.run {
            allowBtn.setOnClickListener {
                dismiss()
                activity.finishAffinity()
                android.os.Process.killProcess(android.os.Process.myPid())
                exitProcess(0)
            }
        }
    }
}