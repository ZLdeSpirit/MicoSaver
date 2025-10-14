package com.m.s.micosaver.channel

import android.os.Bundle
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.m.s.micosaver.BuildConfig
import com.m.s.micosaver.Constant
import com.m.s.micosaver.ex.scope
import com.m.s.micosaver.firebase.FirebaseHelper
import com.m.s.micosaver.ms
import com.reyun.solar.engine.OnAttributionListener
import com.reyun.solar.engine.SolarEngineConfig
import com.reyun.solar.engine.SolarEngineManager
import kotlinx.coroutines.launch
import org.json.JSONObject

object AppChannelHelper {

    val isPro: Boolean
        get() {
            if (BuildConfig.DEBUG) {
                return true
            }
            return FirebaseHelper.remoteConfig.isPro
        }

    private var reyunInitFbId: String? = null

    fun initMarketChannel() {
        if (ms.data.appMarketChannel.isNullOrEmpty()) {
            FirebaseHelper.logEvent("ms_start_market")
            val client = InstallReferrerClient.newBuilder(ms).build()
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(p0: Int) {
                    if (p0 == InstallReferrerClient.InstallReferrerResponse.OK) {
                        val referrerDetails = client.installReferrer
                        val referrer = referrerDetails.installReferrer
                        if (!referrer.isNullOrEmpty()) {
                            ms.data.appMarketChannel = referrer
                        }
                        if (isMarketPro(referrer)) {
                            ms.data.currentProKey = "ms_key_pp"
                            FirebaseHelper.setEventPro()
                            FirebaseHelper.logEvent("ms_pp_market")
                        } else {
                            FirebaseHelper.setEventPro()
                            FirebaseHelper.logEvent("ms_dd_market")
                        }
                    } else {
                        FirebaseHelper.logEvent("ms_dd_market")
                    }
                    try {
                        client.endConnection()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                override fun onInstallReferrerServiceDisconnected() {

                }
            })
        }
    }

    private fun isMarketPro(channel: String?): Boolean {
        if (channel.isNullOrEmpty()) return false
        val channelList = listOf("fb4a", "ig4a", "facebook", "instagram", "fb")
        for (ref in channelList) {
            if (channel.contains(ref, true)) return true
        }
        return false
    }

    fun initReyunChannel(fbId: String?) {
        if (fbId.isNullOrEmpty()) return
        if (fbId == reyunInitFbId && SolarEngineManager.getInstance().initialized.get()) return
        FirebaseHelper.logEvent("ms_start_reyun")
        reyunInitFbId = fbId

        SolarEngineManager.getInstance().apply {
            preInit(ms, Constant.RE_YUN_KEY)
            initialize(ms, Constant.RE_YUN_KEY, createReyunEngine(fbId)) {
                if (it == 0) {
                    scope.launch {
                        // todo
//                        val valueList = DropDatabase.database.proAdValueDao().getAdValueList()
//                        valueList.forEach { value ->
//                            if (AdHelper.uploadAdValue(value)) {
//                                DropDatabase.database.proAdValueDao().delete(value)
//                            }
//                        }
                    }
                    return@initialize
                }
                FirebaseHelper.logEvent("ms_reyun_init_fail", Bundle().apply {
                    putString("msg", it.toString())
                })
            }
        }
    }

    private fun createReyunEngine(fbId: String): SolarEngineConfig {
        val config = SolarEngineConfig.Builder().build()
        config.fbAppID = fbId
        config.attributionListener = object : OnAttributionListener {
            override fun onAttributionSuccess(p0: JSONObject?) {
                val channelId = try {
                    SolarEngineManager.getInstance().attribution?.optString("channel_id")
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
                if (channelId.isNullOrEmpty()) return
                ms.data.reyunChannel = channelId
                if (channelId != "-1") {
                    ms.data.currentProKey = "ms_key_pp"
                    FirebaseHelper.setEventPro()
                    FirebaseHelper.logEvent("ms_pp_reyun")
                } else {
                    FirebaseHelper.logEvent("ms_dd_reyun")
                }
            }

            override fun onAttributionFail(p0: Int) {
                FirebaseHelper.logEvent("ms_reyun_attr_fail", Bundle().apply {
                    putString("msg", p0.toString())
                })
            }

        }
        return config
    }

}