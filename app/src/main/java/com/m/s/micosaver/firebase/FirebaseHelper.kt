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
                    "eyJtc19uYXRpdmUiOnsibXNfbGltaXQiOjIsIm1zX2lkX2xpc3QiOlt7Im1zX2lkIjoiY2EtYXBwLXB1Yi04MTA5NzU2NTAxODEwNTg0Lzc3MzYyMjMwODMiLCJtc19wcmlvcml0eSI6M30seyJtc19pZCI6ImNhLWFwcC1wdWItODEwOTc1NjUwMTgxMDU4NC85ODM5NjQyMjQ4IiwibXNfcHJpb3JpdHkiOjJ9LHsibXNfaWQiOiJjYS1hcHAtcHViLTgxMDk3NTY1MDE4MTA1ODQvNTM3NzY1MzA1NyIsIm1zX3ByaW9yaXR5IjoxfV19LCJtc19pbnRlcnMiOnsibXNfbGltaXQiOjIsIm1zX2lkX2xpc3QiOlt7Im1zX2lkIjoiY2EtYXBwLXB1Yi04MTA5NzU2NTAxODEwNTg0Lzk1Mjc4MTY5MDAiLCJtc19wcmlvcml0eSI6M30seyJtc19pZCI6ImNhLWFwcC1wdWItODEwOTc1NjUwMTgxMDU4NC82MTA0Mjg3NjU0IiwibXNfcHJpb3JpdHkiOjJ9LHsibXNfaWQiOiJjYS1hcHAtcHViLTgxMDk3NTY1MDE4MTA1ODQvNTc5MjU4NjAyMyIsIm1zX3ByaW9yaXR5IjoxfV19LCJtc19vcGVuIjp7Im1zX2xpbWl0IjoxLCJtc19pZF9saXN0IjpbeyJtc19pZCI6ImNhLWFwcC1wdWItODEwOTc1NjUwMTgxMDU4NC80NjcxMDc2MDQ4IiwibXNfcHJpb3JpdHkiOjQsIm1zX3R5cGUiOiJtc19vcGVuIn0seyJtc19pZCI6ImNhLWFwcC1wdWItODEwOTc1NjUwMTgxMDU4NC82MDk1ODM4MzU4IiwibXNfcHJpb3JpdHkiOjMsIm1zX3R5cGUiOiJtc19pbnRlcnMifSx7Im1zX2lkIjoiY2EtYXBwLXB1Yi04MTA5NzU2NTAxODEwNTg0LzYzMTYwNDA2NDQiLCJtc19wcmlvcml0eSI6MiwibXNfdHlwZSI6Im1zX2ludGVycyJ9LHsibXNfaWQiOiJjYS1hcHAtcHViLTgxMDk3NTY1MDE4MTA1ODQvOTA0OTMwNDc1MiIsIm1zX3ByaW9yaXR5IjoxLCJtc190eXBlIjoibXNfaW50ZXJzIn1dfX0="
                )
                put(
                    "ms_ad_pos_info_config",
                    "WwogIHsKICAgICJtc19wb3MiOiAibXNfd2VsY29tZSIsCiAgICAibXNfZW5hYmxlIjogMAogIH0sCiAgewogICAgIm1zX3BvcyI6ICJtc19sYW5faW50ZXJzIiwKICAgICJtc19lbmFibGUiOiAxCiAgfSwKICB7CiAgICAibXNfcG9zIjogIm1zX2xhbl9uYXRpdmUiLAogICAgIm1zX2VuYWJsZSI6IDEKICB9LAogIHsKICAgICJtc19wb3MiOiAibXNfY2xvc2VfaW50ZXJzIiwKICAgICJtc19lbmFibGUiOiAxCiAgfSwKICB7CiAgICAibXNfcG9zIjogIm1zX21haW5fbmF0aXZlIiwKICAgICJtc19lbmFibGUiOiAwCiAgfSwKICB7CiAgICAibXNfcG9zIjogIm1zX21haW5faW50ZXJzIiwKICAgICJtc19lbmFibGUiOiAxCiAgfSwKICB7CiAgICAibXNfcG9zIjogIm1zX3BhcnNlX25hdGl2ZSIsCiAgICAibXNfZW5hYmxlIjogMAogIH0sCiAgewogICAgIm1zX3BvcyI6ICJtc19wYXJzZV9pbnRlcnMiLAogICAgIm1zX2VuYWJsZSI6IDAKICB9LAogIHsKICAgICJtc19wb3MiOiAibXNfZG93bmxvYWRfaW50ZXJzIiwKICAgICJtc19lbmFibGUiOiAxCiAgfSwKICB7CiAgICAibXNfcG9zIjogIm1zX3NhdmVkX25hdGl2ZSIsCiAgICAibXNfZW5hYmxlIjogMQogIH0sCiAgewogICAgIm1zX3BvcyI6ICJtc19lbHNlX25hdGl2ZSIsCiAgICAibXNfZW5hYmxlIjogMQogIH0KXQ=="
                )
                put("ms_fb_value_mul", 1.0)
                put("ms_channel_max_time", 2 * DateUtils.DAY_IN_MILLIS)
                put(
                    "ms_defa_guide_posts_config",
                    "WwogICJodHRwczovL3d3dy5pbnN0YWdyYW0uY29tL3JlZWxzL0RRemk2UnlFenpiLyIsCiAgImh0dHBzOi8vd3d3Lmluc3RhZ3JhbS5jb20vcmVlbHMvRFE5SjNEeWlKSVEvIiwKICAiaHR0cHM6Ly93d3cuaW5zdGFncmFtLmNvbS9yZWVscy9EUHlFNDdqalhreS8iLAogICJodHRwczovL3d3dy5pbnN0YWdyYW0uY29tL3JlZWxzL0RQdi1pbVNFOHUyLyIKXQ=="
                )
                put("ms_hot_open_enable", 1)
                put("ms_ad_sh_loading_state", 0)
                put("ms_ad_sh_pre_enable", true)
                put("ms_ad_fail_retry_enable", false)

            }
        }

        val isPro: Boolean
            get() = Firebase.remoteConfig.getBoolean(ms.data.currentProKey)

        //ewogICJtc19pZCI6ICIxMjM0NTYiLAogICJtc190b2tlbiI6ICIxMjM0NTYiCn0=
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