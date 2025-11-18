package com.m.s.micosaver.ad

import android.os.SystemClock
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.nativead.NativeAd
import com.m.s.micosaver.channel.AppChannelHelper
import com.m.s.micosaver.databinding.MsLayoutNativeBuyAdBinding
import com.m.s.micosaver.databinding.MsLayoutNativeNormalAdBinding
import com.m.s.micosaver.firebase.FirebaseHelper
import com.m.s.micosaver.ui.base.BaseActivity
import com.m.s.micosaver.utils.Logger

class MsAd(val adId: AdHelper.AdId, val ad: Any, val loadAdType: String) {

    private var isShowed = false
    private var loadTime = SystemClock.elapsedRealtime()

    val isCanUse: Boolean
        get() = !isShowed && SystemClock.elapsedRealtime() - loadTime <= DateUtils.HOUR_IN_MILLIS

    private var showPosition: String? = null
    private var showAdValue: AdValue? = null

    fun show(showConfig: ShowConfig) {
        when (ad) {
            is InterstitialAd -> {
                showInters(showConfig, ad)
            }

            is AppOpenAd -> {
                showOpen(showConfig, ad)
            }

            is NativeAd -> {
                showNative(showConfig, ad)
            }

            else -> {
                Logger.logDebugI("AdManager", "show: ad type not support pos: ${showConfig.position}")
                isShowed = true
                showConfig.close?.invoke()
            }
        }
    }

    private fun showInters(showConfig: ShowConfig, ad: InterstitialAd) {
        isShowed = true
        showPosition = showConfig.position
        ad.setOnPaidEventListener {
            showAdValue = it
            AdHelper.uploadShowAdValue(
                it,
                3,
                adId.id,
                ad.responseInfo.loadedAdapterResponseInfo?.adSourceName,
                showPosition
            )
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                super.onAdDismissedFullScreenContent()
                Logger.logDebugI("AdManager", "show: close ad pos: ${showConfig.position}")
                showConfig.close?.invoke()
            }

            override fun onAdClicked() {
                super.onAdClicked()
                clickAd()
            }

            override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                super.onAdFailedToShowFullScreenContent(p0)
                Logger.logDebugI("AdManager", "show: show error: ${p0.message} pos: ${showConfig.position}")
                showConfig.close?.invoke()
            }

            override fun onAdShowedFullScreenContent() {
                super.onAdShowedFullScreenContent()
                showAdSuccess()
            }
        }
        ad.show(showConfig.activity)
        callShowAd()
    }

    private fun showOpen(showConfig: ShowConfig, ad: AppOpenAd) {
        isShowed = true
        showPosition = showConfig.position
        ad.setOnPaidEventListener {
            showAdValue = it
            AdHelper.uploadShowAdValue(
                it,
                2,
                adId.id,
                ad.responseInfo.loadedAdapterResponseInfo?.adSourceName,
                showPosition
            )
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                super.onAdDismissedFullScreenContent()
                Logger.logDebugI("AdManager", "show: close ad pos: ${showConfig.position}")
                showConfig.close?.invoke()
            }

            override fun onAdClicked() {
                super.onAdClicked()
                clickAd()
            }

            override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                super.onAdFailedToShowFullScreenContent(p0)
                Logger.logDebugI("AdManager", "show: show error: ${p0.message} pos: ${showConfig.position}")
                showConfig.close?.invoke()
            }

            override fun onAdShowedFullScreenContent() {
                super.onAdShowedFullScreenContent()
                showAdSuccess()
            }
        }
        ad.show(showConfig.activity)
        callShowAd()
    }

    private fun showNative(showConfig: ShowConfig, ad: NativeAd) {
        val nativeLayout = showConfig.nativeLayout
        if (nativeLayout == null) {
            Logger.logDebugI("AdManager", "show: native layout is null pos: ${showConfig.position}")
            showConfig.close?.invoke()
            return
        }
        showPosition = showConfig.position
        isShowed = true
        nativeLayout.isVisible = true
        ad.setOnPaidEventListener {
            showAdValue = it
            AdHelper.uploadShowAdValue(
                it,
                6,
                adId.id,
                ad.responseInfo?.loadedAdapterResponseInfo?.adSourceName,
                showPosition
            )
        }
        ShowNativeAd().show(ad, nativeLayout)
        callShowAd()
        showAdSuccess()
    }

    fun clickAd() {
        Logger.logDebugI("AdManager", "click ad pos: $showPosition")
        showAdValue?.let {
            AdHelper.uploadClickAdValue(it)
        }
        showPosition?.let {
            FirebaseHelper.logEvent("ms_ad_click_$it")
        }
    }

    private fun callShowAd() {
        if (FirebaseHelper.remoteConfig.adShowPreloadEnable) {
            AdHelper.preload1(loadAdType)
        }
        showPosition?.let {
            FirebaseHelper.logEvent("ms_ad_call_$it")
        }
    }

    private fun showAdSuccess() {
        Logger.logDebugI("AdManager", "show: show ad success pos: $showPosition")
        FirebaseHelper.logEvent("ms_ad_show_$showPosition")
    }

    inner class ShowNativeAd {

        fun show(ad: NativeAd, nativeLayout: FrameLayout) {
            if (AppChannelHelper.isPro) {
                showBuyLayout(ad, nativeLayout)
            } else {
                showNormalLayout(ad, nativeLayout)
            }
        }

        private fun showBuyLayout(ad: NativeAd, nativeLayout: FrameLayout) {
            val binding =
                MsLayoutNativeBuyAdBinding.inflate(LayoutInflater.from(nativeLayout.context))
            binding.run {
                root.headlineView = titleTv
                root.bodyView = descTv
                root.mediaView = mediaView
                root.callToActionView = actionBtn
                root.iconView = iconIv
                (root.headlineView as TextView).text = ad.headline
                root.bodyView?.isVisible = false
                root.callToActionView?.isVisible = false
                root.iconView?.isVisible = false
                ad.callToAction?.let {
                    root.callToActionView?.isVisible = true
                    (root.callToActionView as TextView).text = it
                }
                ad.icon?.let {
                    root.iconView?.isVisible = true
                    (root.iconView as ImageView).setImageDrawable(it.drawable)
                }
                ad.mediaContent?.let {
                    root.mediaView?.mediaContent = it
                }
                ad.body?.let {
                    root.bodyView?.isVisible = true
                    (root.bodyView as TextView).text = it
                }
                root.setNativeAd(ad)
                nativeLayout.removeAllViews()
                nativeLayout.addView(root)
            }
            val padding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 10f, nativeLayout.context.resources.displayMetrics
            ).toInt()
            nativeLayout.setPadding(padding, 0, padding, padding)
            nativeLayout.background = null
        }

        private fun showNormalLayout(ad: NativeAd, nativeLayout: FrameLayout) {
            val binding =
                MsLayoutNativeNormalAdBinding.inflate(LayoutInflater.from(nativeLayout.context))
            binding.run {
                root.headlineView = titleTv
                root.bodyView = descTv
                root.mediaView = mediaView
                root.callToActionView = actionBtn
                root.iconView = iconIv
                (root.headlineView as TextView).text = ad.headline
                root.bodyView?.isVisible = false
                root.callToActionView?.isVisible = false
                root.iconView?.isVisible = false
                ad.callToAction?.let {
                    root.callToActionView?.isVisible = true
                    (root.callToActionView as TextView).text = it
                }
                ad.icon?.let {
                    root.iconView?.isVisible = true
                    (root.iconView as ImageView).setImageDrawable(it.drawable)
                }
                ad.mediaContent?.let {
                    root.mediaView?.mediaContent = it
                }
                ad.body?.let {
                    root.bodyView?.isVisible = true
                    (root.bodyView as TextView).text = it
                }
                root.setNativeAd(ad)
                nativeLayout.removeAllViews()
                nativeLayout.addView(root)
            }
        }

    }

    class ShowConfig(val activity: BaseActivity, val position: String) {

        var close: (() -> Unit)? = null
            private set

        var nativeLayout: FrameLayout? = null
            private set

        fun setCloseCallback(close: () -> Unit): ShowConfig {
            this.close = close
            return this
        }

        fun setNativeLayout(nativeLayout: FrameLayout): ShowConfig {
            this.nativeLayout = nativeLayout
            return this
        }

    }

}