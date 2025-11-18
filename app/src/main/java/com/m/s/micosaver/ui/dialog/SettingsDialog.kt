package com.m.s.micosaver.ui.dialog

import android.content.Intent
import android.view.View
import com.m.s.micosaver.BuildConfig
import com.m.s.micosaver.ad.AdHelper
import com.m.s.micosaver.databinding.MsDialogSettingsBinding
import com.m.s.micosaver.firebase.FirebaseHelper
import com.m.s.micosaver.ui.activity.MsHowToUseActivity
import com.m.s.micosaver.ui.activity.MsLanguageActivity
import com.m.s.micosaver.ui.base.BaseActivity

class SettingsDialog(
    private val activity: BaseActivity,
) : BaseDialog1(activity, false) {

    private val binding by lazy { MsDialogSettingsBinding.inflate(layoutInflater) }


    override fun onRootView(): View {
        return binding.root
    }

    override fun show() {
        super.show()
        FirebaseHelper.logEvent("ms_scene_settings")
        activity.loadNativeAd(AdHelper.Position.ELSE_NATIVE to binding.nativeContainer)
    }

    override fun dismiss() {
        super.dismiss()
        activity.cancelLoadNativeAd()
    }

    override fun onInitView() {
        binding.run {
            val versionName = BuildConfig.VERSION_NAME
            versionTv.text = versionName

            dialogCloseIv.setOnClickListener {
                dismiss()
            }

            languageCl.setOnClickListener {
                activity.startActivity(Intent(activity, MsLanguageActivity::class.java))
                dismiss()
            }

            howToUseCl.setOnClickListener {
                goToHowToUse()
            }

            clickMeBtn.setOnClickListener {
                goToHowToUse()
            }
        }
    }

    private fun goToHowToUse() {
        activity.startActivity(Intent(activity, MsHowToUseActivity::class.java))
        dismiss()
    }

}