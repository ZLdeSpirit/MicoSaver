package com.m.s.micosaver.ui.dialog

import android.view.LayoutInflater
import android.view.View
import com.m.s.micosaver.databinding.MsDialogDeleteTipBinding
import com.m.s.micosaver.ui.base.BaseActivity

class DeleteTipDialog (activity: BaseActivity,
                       val title: String,
                       val desc: String,
                       private val remove: () -> Unit
) : BaseDialog2(activity, false) {
    private val binding by lazy { MsDialogDeleteTipBinding.inflate(LayoutInflater.from(context)) }

    override fun onRootView(): View {
        return binding.root
    }

    override fun onInitView() {
        binding.run {
            deleteTipTitleTv.text = title
            deleteTipDescTv.text = desc

            cancelBtn.setOnClickListener {
                dismiss()
            }

            removeBtn.setOnClickListener {
                dismiss()
                remove.invoke()
            }
        }
    }
}