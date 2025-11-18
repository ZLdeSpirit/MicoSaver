package com.m.s.micosaver.firebase

import android.os.Bundle
import android.text.format.DateUtils
import android.util.Base64
import android.util.Log
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.analytics
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.remoteConfig
import com.m.s.micosaver.ad.AdHelper
import com.m.s.micosaver.channel.AppChannelHelper
import com.m.s.micosaver.ms
import org.json.JSONObject

object FirebaseHelper {

    val remoteConfig by lazy { RemoteConfig() }

    fun initFirebase() {
        try {
            FirebaseApp.initializeApp(ms)
            initEvent()
            remoteConfig.initRemoteConfig()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setEventPro() {
        val pro = if (AppChannelHelper.isPro) {
            "pp"
        } else {
            if (ms.data.appMarketChannel.isNullOrEmpty() && ms.data.reyunChannel.isNullOrEmpty()) {
                "nn"
            } else {
                "dd"
            }
        }
        Firebase.analytics.setUserProperty("pro", pro)
        Log.d("AnalyticsManager", "pro: $pro")
    }

    fun logEvent(name: String, params: Bundle? = null) {
        Log.d("AnalyticsManager", "event: $name params: $params")
        Firebase.analytics.logEvent(name, params)
    }

    private fun initEvent() {
        setEventPro()
        var day = (System.currentTimeMillis() - ms.appInstallTime) / DateUtils.DAY_IN_MILLIS
        if (day < 0) day = 0
        Firebase.analytics.setUserProperty("day", day.toString())
        Log.d("AnalyticsManager", "day: $day")
    }


    class RemoteConfig : ConfigUpdateListener, OnCompleteListener<Boolean> {

        fun initRemoteConfig() {
            Firebase.remoteConfig.run {
                setDefaultsAsync(getRemoteConfigDefault())
                addOnConfigUpdateListener(this@RemoteConfig)
                fetchAndActivate().addOnCompleteListener(this@RemoteConfig)
            }
        }

        private fun getRemoteConfigDefault(): Map<String, Any> {
            return hashMapOf<String, Any>().apply {
                // TODO 修改默认配置
                put("ms_key_pp", true)
                put("ms_key_dd", false)
                put(
                    "ms_ad_info_config",
                    "ewogICAgIm1zX25hdGl2ZSI6IHsKICAgICAgICAibXNfbGltaXQiOiAyLAogICAgICAgICJtc19pZF9saXN0IjogWwogICAgICAgICAgICB7CiAgICAgICAgICAgICAgICAibXNfaWQiOiAiY2EtYXBwLXB1Yi0zOTQwMjU2MDk5OTQyNTQ0LzIyNDc2OTYxMTAiLAogICAgICAgICAgICAgICAgIm1zX3ByaW9yaXR5IjogMQogICAgICAgICAgICB9CiAgICAgICAgXQogICAgfSwKICAgICJtc19pbnRlcnMiOiB7CiAgICAgICAgIm1zX2xpbWl0IjogMiwKICAgICAgICAibXNfaWRfbGlzdCI6IFsKICAgICAgICAgICAgewogICAgICAgICAgICAgICAgIm1zX2lkIjogImNhLWFwcC1wdWItMzk0MDI1NjA5OTk0MjU0NC8xMDMzMTczNzEyIiwKICAgICAgICAgICAgICAgICJtc19wcmlvcml0eSI6IDEKICAgICAgICAgICAgfQogICAgICAgIF0KICAgIH0sCiAgICAibXNfb3BlbiI6IHsKICAgICAgICAibXNfbGltaXQiOiAxLAogICAgICAgICJtc19pZF9saXN0IjogWwogICAgICAgICAgICB7CiAgICAgICAgICAgICAgICAibXNfaWQiOiAiY2EtYXBwLXB1Yi0zOTQwMjU2MDk5OTQyNTQ0LzkyNTczOTU5MjEiLAogICAgICAgICAgICAgICAgIm1zX3ByaW9yaXR5IjogMiwKICAgICAgICAgICAgICAgICJtc190eXBlIjogIm1zX29wZW4iCiAgICAgICAgICAgIH0sCiAgICAgICAgICAgIHsKICAgICAgICAgICAgICAgICJtc19pZCI6ICJjYS1hcHAtcHViLTM5NDAyNTYwOTk5NDI1NDQvMTAzMzE3MzcxMiIsCiAgICAgICAgICAgICAgICAibXNfcHJpb3JpdHkiOiAxLAogICAgICAgICAgICAgICAgIm1zX3R5cGUiOiAibXNfaW50ZXJzIgogICAgICAgICAgICB9CiAgICAgICAgXQogICAgfQp9"
                )
                put(
                    "ms_ad_pos_info_config",
                    "WwogIHsKICAgICJtc19wb3MiOiAibXNfd2VsY29tZSIsCiAgICAibXNfZW5hYmxlIjogMAogIH0sCiAgewogICAgIm1zX3BvcyI6ICJtc19sYW5faW50ZXJzIiwKICAgICJtc19lbmFibGUiOiAxCiAgfSwKICB7CiAgICAibXNfcG9zIjogIm1zX2xhbl9uYXRpdmUiLAogICAgIm1zX2VuYWJsZSI6IDEKICB9LAogIHsKICAgICJtc19wb3MiOiAibXNfY2xvc2VfaW50ZXJzIiwKICAgICJtc19lbmFibGUiOiAxCiAgfSwKICB7CiAgICAibXNfcG9zIjogIm1zX21haW5fbmF0aXZlIiwKICAgICJtc19lbmFibGUiOiAwCiAgfSwKICB7CiAgICAibXNfcG9zIjogIm1zX21haW5faW50ZXJzIiwKICAgICJtc19lbmFibGUiOiAxCiAgfSwKICB7CiAgICAibXNfcG9zIjogIm1zX3BhcnNlX25hdGl2ZSIsCiAgICAibXNfZW5hYmxlIjogMAogIH0sCiAgewogICAgIm1zX3BvcyI6ICJtc19wYXJzZV9pbnRlcnMiLAogICAgIm1zX2VuYWJsZSI6IDAKICB9LAogIHsKICAgICJtc19wb3MiOiAibXNfZG93bmxvYWRfaW50ZXJzIiwKICAgICJtc19lbmFibGUiOiAxCiAgfSwKICB7CiAgICAibXNfcG9zIjogIm1zX3NhdmVkX25hdGl2ZSIsCiAgICAibXNfZW5hYmxlIjogMQogIH0sCiAgewogICAgIm1zX3BvcyI6ICJtc19lbHNlX25hdGl2ZSIsCiAgICAibXNfZW5hYmxlIjogMQogIH0KXQ=="
                )
                put("ms_fb_value_mul", 1.0)
                put("ms_channel_max_time", 2 * DateUtils.DAY_IN_MILLIS)
                put(
                    "ms_defa_guide_posts_config",
                    "WyJodHRwczovL3d3dy5pbnN0YWdyYW0uY29tL3JlZWwvRFBLUXVRMkU2RDcvIiwiaHR0cHM6Ly93d3cuaW5zdGFncmFtLmNvbS9yZWVsL0RQQk54b2JERFpSLyIsImh0dHBzOi8vd3d3Lmluc3RhZ3JhbS5jb20vcmVlbC9EUElhNFE1alhwUi8iLCJodHRwczovL3d3dy5pbnN0YWdyYW0uY29tL3JlZWwvRFBLOUN4bWtlUzgvIl0="
                )
                put("ms_hot_open_enable", 1)
                put("ms_ad_sh_loading_state", 0)
                put("ms_ad_sh_pre_enable", true)
                put("ms_ad_fail_retry_enable", false)

            }
        }

        val isPro: Boolean
            get() = Firebase.remoteConfig.getBoolean(ms.data.currentProKey)

        private val initFacebookConfig: String
            get() {
                return Firebase.remoteConfig.getString("ms_init_face_config")
            }

        val initFacebookInfo: Pair<String, String>?
            get() {
                val config = initFacebookConfig
                if (config.isEmpty()) return null
                try {
                    val json = JSONObject(String(Base64.decode(config, Base64.NO_WRAP)))
                    return json.getString("ms_id") to json.getString("ms_token")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return null
            }

        val adInfoConfig: String
            get() = Firebase.remoteConfig.getString("ms_ad_info_config")

        val adPositionInfoConfig: String
            get() = Firebase.remoteConfig.getString("ms_ad_pos_info_config")

        val facebookValueMul: Double
            get() = Firebase.remoteConfig.getDouble("ms_fb_value_mul")

        val facebookValueLimit: Double
            get() = Firebase.remoteConfig.getDouble("ms_fb_value_limit")

        val channelMaxTime: Long
            get() = Firebase.remoteConfig.getLong("ms_channel_max_time")

        val defaultGuidePostsConfig: String
            get() = Firebase.remoteConfig.getString("ms_defa_guide_posts_config")

        val hotOpenEnable: Int
            get() = Firebase.remoteConfig.getLong("ms_hot_open_enable").toInt()

        val adFailRetryEnable: Boolean
            get() = Firebase.remoteConfig.getBoolean("ms_ad_fail_retry_enable")

        val adShowPreloadEnable: Boolean
            get() = Firebase.remoteConfig.getBoolean("ms_ad_sh_pre_enable")

        val adShowLoadingState: Int
            get() = Firebase.remoteConfig.getLong("ms_ad_sh_loading_state").toInt()

        override fun onUpdate(configUpdate: ConfigUpdate) {
            configChange()
        }

        override fun onError(error: FirebaseRemoteConfigException) {

        }

        override fun onComplete(p0: Task<Boolean>) {
            if (p0.isSuccessful) {
                configChange()
            }
        }

        private fun configChange() {
            ms.initFacebook()
            AdHelper.resetData()
        }

    }

}