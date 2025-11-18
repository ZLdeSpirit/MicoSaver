package com.m.s.micosaver.ui.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.m.s.micosaver.ad.AdHelper
import com.m.s.micosaver.databinding.MsActivitySplashBinding
import com.m.s.micosaver.firebase.FirebaseHelper
import com.m.s.micosaver.helper.LifecycleHelper
import com.m.s.micosaver.helper.ParamsHelper
import com.m.s.micosaver.ms
import com.m.s.micosaver.ui.base.BaseActivity

class MsSplashActivity : BaseActivity(){
    companion object {
        private var isNeedUmp = true
    }

    private var enterType = ParamsHelper.EnterType.UNKNOWN.type
    private var handler = Handler(Looper.getMainLooper())
    private var timeOutRunnable = Runnable {
        showAd()
    }
    private var isShowedAd = false

    private val mBinding by lazy { MsActivitySplashBinding.inflate(layoutInflater) }
    override fun onRootView(): View {
        return mBinding.root
    }

    override fun onSetPageEdge(): Boolean {
        return true
    }

    override fun onInitView() {
        enterType = intent.getStringExtra(ParamsHelper.KEY_ENTER_TYPE)
            ?: ParamsHelper.EnterType.UNKNOWN.type
        openMsg()
        handleMsg()
        FirebaseHelper.logEvent("ms_welcome", Bundle().apply {
            putString("type", enterType)
        })
        FirebaseHelper.logEvent("ms_scene_${onShowFullScreenPosition()}")
    }

    @SuppressLint("InlinedApi")
    private fun openMsg() {
        if (ms.isOpenMsg) {
            startUmp()
        } else {
            val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                loadAd()
            }
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun loadAd() {
        handler.postDelayed(timeOutRunnable, 15000)
        load(onShowFullScreenPosition()) {
            showAd()
        }
        preloadAd()
    }

    private fun showAd() {
        handler.removeCallbacks(timeOutRunnable)
        if (isShowedAd) return
        isShowedAd = true
        showFullScreen(false) {
            if (isDestroyed) return@showFullScreen
            if (enterType == ParamsHelper.EnterType.HOT_OPEN.type || (!isVisiblePage && !LifecycleHelper.isForeground)) {
                finish()
                return@showFullScreen
            }
            startActivity(
                Intent(
                    this, if (ms.data.isFirstOpen) {
                        MsLanguageActivity::class.java
                    } else {
                        MainActivity::class.java
                    }
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    intent.extras?.let {
                        putExtras(it)
                    }
                    if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
                        putExtra(ParamsHelper.KEY_ENTER_TYPE, ParamsHelper.EnterType.SHARE.type)
                        putExtra(
                            ParamsHelper.KEY_PARSE_URL,
                            intent.getStringExtra(Intent.EXTRA_TEXT)
                        )
                    }
                })
            finish()
        }
    }

    override fun onClose() {

    }

    override fun onResetData() {
        super.onResetData()
        handler.removeCallbacks(timeOutRunnable)
    }

    private fun handleMsg() {
        when (enterType) {
            ParamsHelper.EnterType.SAVED.type, ParamsHelper.EnterType.PARSE.type -> {
                FirebaseHelper.logEvent("ms_msg_click", Bundle().apply {
                    putString("type", enterType)
                })
                removeMsg(intent.getIntExtra(ParamsHelper.KEY_MSG_ID, -1))
            }

            ParamsHelper.EnterType.SAVING.type -> {
                FirebaseHelper.logEvent("ms_msg_click", Bundle().apply {
                    putString("type", enterType)
                })
            }
        }
    }

    private fun removeMsg(msgId: Int) {
        if (msgId <= 0) return
        try {
            NotificationManagerCompat.from(this).cancel(msgId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startUmp() {
        if (!isNeedUmp) {
            loadAd()
        } else {
            val consentInformation = UserMessagingPlatform.getConsentInformation(this)
            consentInformation.requestConsentInfoUpdate(
                this,
                ConsentRequestParameters.Builder().build(),
                {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { _ ->
                        isNeedUmp = false
                        if (consentInformation.canRequestAds()) {
                            AdHelper.initAd()
                        }
                        loadAd()
                    }
                },
                {
                    isNeedUmp = false
                    loadAd()
                })
        }
    }


    override fun onShowCloseAd(): Boolean {
        return false
    }

    override fun onShowFullScreenPosition(): String {
        return AdHelper.Position.WELCOME
    }

    override fun onCallPreloadList(): List<String> {
        return if (ms.data.isFirstOpen) {
            listOf(AdHelper.Position.LAN_NATIVE, AdHelper.Position.LAN_INTERS)
        } else {
            listOf(
                AdHelper.Position.MAIN_INTERS,
                AdHelper.Position.MAIN_NATIVE,
                AdHelper.Position.PARSE_INTERS,
                AdHelper.Position.PARSE_NATIVE
            )
        }
    }
}