package com.m.s.micosaver.ad

import android.os.Bundle
import android.text.format.DateUtils
import android.util.Base64
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsConstants
import com.facebook.appevents.AppEventsLogger
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.firebase.analytics.FirebaseAnalytics
import com.m.s.micosaver.channel.AppChannelHelper
import com.m.s.micosaver.db.MsDataBase
import com.m.s.micosaver.db.info.MsAdValue
import com.m.s.micosaver.ex.scope
import com.m.s.micosaver.firebase.FirebaseHelper
import com.m.s.micosaver.ms
import com.m.s.micosaver.ui.dialog.AdLoadingDialog
import com.m.s.micosaver.utils.Logger
import com.reyun.solar.engine.SolarEngineManager
import com.reyun.solar.engine.infos.SEAdImpEventModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.util.Currency
import kotlin.math.pow

object AdHelper {

    private val loadManager by lazy { AdLoadManager() }

    object Type {
        const val INTERS = "ms_inters"
        const val NATIVE = "ms_native"
        const val OPEN = "ms_open"
    }

    object Position {
        const val WELCOME = "ms_welcome"
        const val LAN_INTERS = "ms_lan_inters"
        const val LAN_NATIVE = "ms_lan_native"
        const val CLOSE_INTERS = "ms_close_inters"
        const val MAIN_NATIVE = "ms_main_native"
        const val MAIN_INTERS = "ms_main_inters"
        const val PARSE_NATIVE = "ms_parse_native"
        const val PARSE_INTERS = "ms_parse_inters"
        const val DOWNLOAD_INTERS = "ms_download_inters"
        const val SAVED_NATIVE = "ms_saved_native"
        const val ELSE_NATIVE = "ms_else_native"

        private val enableMap = hashMapOf<String, Int>()
        private var hasConfig = false

        fun isOpenLoading(position: String): Boolean {
            val isEnableLoading = when (position) {
                WELCOME -> false
                CLOSE_INTERS -> true
                MAIN_NATIVE -> false
                MAIN_INTERS -> true
                PARSE_NATIVE -> false
                PARSE_INTERS -> true
                DOWNLOAD_INTERS -> true
                SAVED_NATIVE -> false
                ELSE_NATIVE -> false
                LAN_INTERS -> true
                LAN_NATIVE -> false
                else -> false
            }
            if (!isEnableLoading) return false
            if (!isEnable(position)) return false
            return when (FirebaseHelper.remoteConfig.adShowLoadingState) {
                0 -> true
                1 -> AppChannelHelper.isPro
                2 -> !AppChannelHelper.isPro
                else -> false
            }
        }

        fun getAdType(position: String): String? {
            return when (position) {
                WELCOME -> Type.OPEN
                CLOSE_INTERS -> Type.INTERS
                MAIN_NATIVE -> Type.NATIVE
                MAIN_INTERS -> Type.INTERS
                PARSE_NATIVE -> Type.NATIVE
                PARSE_INTERS -> Type.INTERS
                DOWNLOAD_INTERS -> Type.INTERS
                SAVED_NATIVE -> Type.NATIVE
                ELSE_NATIVE -> Type.NATIVE
                LAN_INTERS -> Type.INTERS
                LAN_NATIVE -> Type.NATIVE
                else -> null
            }
        }

        fun isEnable(position: String): Boolean {
            if (!hasConfig) {
                val config = FirebaseHelper.remoteConfig.adPositionInfoConfig
                if (config.isEmpty()) return false
                try {
                    val array = JSONArray(String(Base64.decode(config, Base64.NO_WRAP)))
                    for (i in 0 until array.length()) {
                        val jsonObject = array.getJSONObject(i)
                        val key = jsonObject.getString("ms_pos")
                        val enable = jsonObject.getInt("ms_enable")
                        enableMap[key] = enable
                    }
                    hasConfig = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            val enable = enableMap[position] ?: return false
            return when (enable) {
                0 -> true
                1 -> AppChannelHelper.isPro
                2 -> !AppChannelHelper.isPro
                else -> false
            }
        }

        fun resetData() {
            enableMap.clear()
            hasConfig = false
        }
    }

    private fun isEnable(position: String): Boolean {
        return Position.isEnable(position)
    }

    fun preload1(type: String) {
        Logger.logDebugI("AdManager", "preload: start preload type: $type")
        loadManager.preload(type)
    }

    fun preload(vararg position: String) {
        position.forEach { pos ->
            preload2(pos)
        }
    }

    private fun preload2(position: String) {
        Logger.logDebugI("AdManager", "preload: start preload pos: $position")
        if (!isEnable(position)) {
            Logger.logDebugI("AdManager", "preload: pos is not enable pos: $position")
            return
        }
        Position.getAdType(position)?.let {
            preload1(it)
        }
    }

    fun load(position: String, callback: () -> Unit) {
        Logger.logDebugI("AdManager", "load: start load pos: $position")
        if (!isEnable(position)) {
            Logger.logDebugI("AdManager", "load: pos is not enable pos: $position")
            callback.invoke()
            return
        }
        val type = Position.getAdType(position)
        if (type == null) {
            Logger.logDebugI("AdManager", "load: type is null pos: $position")
            callback.invoke()
            return
        }
        loadManager.load(type, callback)
    }

    fun show(showConfig: MsAd.ShowConfig) {
        Logger.logDebugI("AdManager", "show: start show pos: ${showConfig.position}")
        if (!showConfig.activity.isVisiblePage) {
            Logger.logDebugI("AdManager", "show: page is not visible pos: ${showConfig.position}")
            showConfig.close?.invoke()
            return
        }
        getShowAd(showConfig, getAd(showConfig.position)) {
            if (it == null) {
                Logger.logDebugI("AdManager", "show: ad is null pos: ${showConfig.position}")
                showConfig.close?.invoke()
                return@getShowAd
            }
            it.show(showConfig)
        }
    }

    private fun getShowAd(
        showConfig: MsAd.ShowConfig,
        ad: MsAd?,
        callback: (MsAd?) -> Unit
    ) {
        if (Position.isOpenLoading(showConfig.position)) {
            val dialog = AdLoadingDialog(showConfig.activity).showDialog()
            if (ad == null) {
                var isTimeOut = false
                val job = scope.launch {
                    delay(5000)
                    isTimeOut = true
                    withContext(Dispatchers.Main) {
                        dialog.dismiss()
                        callback.invoke(ad)
                    }
                }
                load(showConfig.position) {
                    if (isTimeOut) return@load
                    job.cancel()
                    dialog.dismiss()
                    callback.invoke(getAd(showConfig.position))
                }
            } else {
                scope.launch {
                    delay(500)
                    withContext(Dispatchers.Main) {
                        dialog.dismiss()
                        callback.invoke(ad)
                    }
                }
            }
        } else {
            callback.invoke(ad)
        }
    }

    private fun getAd(position: String): MsAd? {
        if (!isEnable(position)) {
            Logger.logDebugI("AdManager", "getProAd: pos is not enable pos: $position")
            return null
        }
        val type = Position.getAdType(position)
        if (type.isNullOrEmpty()) {
            Logger.logDebugI("AdManager", "getProAd: type is null pos: $position")
            return null
        }
        return loadManager.getProAd(type)
    }


    fun initAd() {
        try {
            MobileAds.initialize(ms)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun resetData() {
        loadManager.resetData()
        Position.resetData()
    }

    fun facebookInitSuccess() {
        AdValueManager().facebookInitSuccess()
    }

    fun uploadShowAdValue(
        adValue: AdValue,
        adType: Int,
        adId: String,
        sourceName: String?,
        position: String?
    ) {
        AdValueManager().uploadShowAdValue(adValue, adType, adId, sourceName, position)
    }

    fun uploadClickAdValue(adValue: AdValue) {
        AdValueManager().uploadClickAdValue(adValue)
    }

    fun uploadAdValue(value: MsAdValue): Boolean {
        return AdValueManager().uploadAdValueToReyun(value)
    }

    internal class AdValueManager {

        fun facebookInitSuccess() {
            if (!FacebookSdk.isInitialized()) return
            var value = ms.data.getAdValue("click")
            if (value.first > 0 && !value.second.isNullOrEmpty()) {
                AppEventsLogger.newLogger(ms).logEvent(
                    AppEventsConstants.EVENT_NAME_AD_CLICK,
                    value.first,
                    Bundle().apply {
                        putString(AppEventsConstants.EVENT_PARAM_CURRENCY, value.second)
                    })
                ms.data.setAdValue("click", 0.0, null)
            }
            value = ms.data.getAdValue("show")
            if (value.first > 0 && !value.second.isNullOrEmpty()) {
                val uploadValue = value.first * FirebaseHelper.remoteConfig.facebookValueMul
                val logger = AppEventsLogger.newLogger(ms)
                logger.logPurchase(
                    BigDecimal.valueOf(uploadValue),
                    Currency.getInstance(value.second)
                )
                logger.logEvent(
                    AppEventsConstants.EVENT_NAME_AD_IMPRESSION,
                    uploadValue,
                    Bundle().apply {
                        putString(AppEventsConstants.EVENT_PARAM_CURRENCY, value.second)
                    })
                ms.data.setAdValue("show", 0.0, null)
            }
        }

        fun uploadClickAdValue(adValue: AdValue) {
            val value = adValue.valueMicros / 1000000.0
            if (FacebookSdk.isInitialized()) {
                val logger = AppEventsLogger.newLogger(ms)
                logger.logEvent(AppEventsConstants.EVENT_NAME_AD_CLICK, value, Bundle().apply {
                    putString(AppEventsConstants.EVENT_PARAM_CURRENCY, adValue.currencyCode)
                })
            } else {
                ms.data.setAdValue("click", value, adValue.currencyCode)
            }
        }

        fun uploadShowAdValue(
            adValue: AdValue,
            adType: Int,
            adId: String,
            sourceName: String?,
            position: String?
        ) {
            val value = adValue.valueMicros / 1000000.0
            if (FacebookSdk.isInitialized()) {
                val newVal = value * FirebaseHelper.remoteConfig.facebookValueMul
                val logger = AppEventsLogger.newLogger(ms)
                logger.logPurchase(
                    BigDecimal.valueOf(newVal), Currency.getInstance(adValue.currencyCode)
                )
                logger.logEvent(
                    AppEventsConstants.EVENT_NAME_AD_IMPRESSION,
                    newVal,
                    Bundle().apply {
                        putString(AppEventsConstants.EVENT_PARAM_CURRENCY, adValue.currencyCode)
                    })
            } else {
                ms.data.setAdValue("show", value, adValue.currencyCode)
            }
            if (ms.data.isUploadFacebookValue) {
                val cumValue = ms.data.getAdValue("mul").first + value
                ms.data.setAdValue("mul", cumValue, adValue.currencyCode, true)
                val maxValue = FirebaseHelper.remoteConfig.facebookValueLimit
                if (FacebookSdk.isInitialized() && maxValue > 0 && cumValue >= maxValue) {
                    ms.data.isUploadFacebookValue = false
                    AppEventsLogger.newLogger(ms)
                        .logEvent("ms_limit_value", cumValue, Bundle().apply {
                            putString(AppEventsConstants.EVENT_PARAM_CURRENCY, adValue.currencyCode)
                        })
                }
            }
            position?.let {
                FirebaseHelper.logEvent("ms_ad_value_${position}", Bundle().apply {
                    putString(FirebaseAnalytics.Param.CURRENCY, adValue.currencyCode)
                    putDouble(FirebaseAnalytics.Param.VALUE, value)
                })
            }
            val totalValue = ms.data.getAdValue("firebase").first + value
            if (totalValue >= 0.01) {
                ms.data.setAdValue("firebase", 0.0, null, true)
                FirebaseHelper.logEvent("ms_ad_value001", Bundle().apply {
                    putString(FirebaseAnalytics.Param.CURRENCY, adValue.currencyCode)
                    putDouble(FirebaseAnalytics.Param.VALUE, totalValue)
                })
            } else {
                ms.data.setAdValue("firebase", totalValue, adValue.currencyCode, true)
            }
            val proAdValue =
                MsAdValue(adValue.valueMicros, adValue.currencyCode, adId, adType, sourceName)
            if (!uploadAdValueToReyun(proAdValue)) {
                scope.launch {
                    MsDataBase.database.proAdValueDao().insert(proAdValue)
                }
            }
        }

        fun uploadAdValueToReyun(value: MsAdValue): Boolean {
            if (!SolarEngineManager.getInstance().initialized.get()) return false
            try {
                SolarEngineManager.getInstance().trackAdImpression(SEAdImpEventModel().apply {
                    //展示广告的类型，实际接入的广告类型,以此例激励视频为例adType = 1
                    setAdType(value.type)
                    //变现平台的应用ID
//            setAdNetworkAppID(adSourceId)
                    //变现平台的变现广告位ID
                    setAdNetworkADID(value.adId)
                    //广告ECPM
                    setEcpm(value.value / 1000.0)
                    //变现平台货币类型
                    setCurrencyType(value.code)
                    //填充成功填TRUE即可
                    setRenderSuccess(true)
                    //变现平台名称
                    setAdNetworkPlatform(value.sourceName)
                    //聚合平台标识,admob SDK 设置成 "admob"
                    setMediationPlatform("admob")
                })
                return true
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return false
        }
    }

    data class AdId(val id: String, val type: String, val priority: Int) {
        val scene = ""
    }

    data class LoadConfig(val adIdList: List<AdId>, val limit: Int) {
        val fromSource = 0
    }

    internal class AdLoadManager {

        private val loadTaskMap = mutableMapOf<String, AdLoadTask>()

        fun resetData() {
            loadTaskMap.forEach {
                it.value.resetData()
            }
        }

        fun preload(type: String) {
            getLoadTask(type).preload()
        }

        fun load(type: String, callback: () -> Unit) {
            getLoadTask(type).load(callback)
        }

        fun getProAd(type: String): MsAd? {
            return getLoadTask(type).getProAd()
        }

        private fun getLoadTask(type: String): AdLoadTask {
            synchronized(loadTaskMap) {
                return loadTaskMap.getOrPut(type) { AdLoadTask(type) }
            }
        }


        inner class AdLoadTask(private val type: String) {

            private val proAdList = mutableListOf<MsAd>()
            private var finishCallback: (() -> Unit)? = null
            private var loadingCount = 0
            private var failedCount = 0
            private var failedJob: Job? = null
            private var loadConfig: LoadConfig? = null

            private val adSize: Int
                get() {
                    synchronized(proAdList) {
                        val iterator = proAdList.iterator()
                        while (iterator.hasNext()) {
                            if (!iterator.next().isCanUse) {
                                iterator.remove()
                            }
                        }
                        return proAdList.size
                    }
                }

            fun preload() {
                loadAd(null)
            }

            fun load(callback: () -> Unit) {
                var currentCallback: (() -> Unit)? = callback
                val ad = getProAd()
                if (ad != null && currentCallback != null) {
                    Logger.logDebugI("AdManager", "load: has cache ad type: $type")
                    currentCallback.invoke()
                    currentCallback = null
                }
                loadAd(currentCallback)
            }

            private fun loadAd(callback: (() -> Unit)?) {
                checkLoad({
                    loadingCount++
                    callback?.let {
                        finishCallback = it
                    }
                    loadAd(it)
                }, callback)
            }

            private fun loadAd(config: LoadConfig) {
                scope.launch {
                    var ad: MsAd? = null
                    for (adId in config.adIdList) {
                        ad = loadAd(adId)
                        if (ad != null) break
                    }
                    withContext(Dispatchers.Main) {
                        if (ad != null) {
                            addProAd(ad)
                        }
                        loadingCount--
                        finishCallback?.invoke()
                        finishCallback = null
                        if (FirebaseHelper.remoteConfig.adFailRetryEnable) {
                            if (ad == null) {
                                startRetry()
                            } else {
                                cancelRetry()
                            }
                        }
                    }
                }
            }

            private suspend fun loadAd(adId: AdId): MsAd? {
                val type = adId.type.ifEmpty { type }
                return when (type) {
                    Type.OPEN -> loadOpen(adId)
                    Type.INTERS -> loadInters(adId)
                    Type.NATIVE -> loadNative(adId)
                    else -> {
                        Logger.logDebugI("AdManager", "load: adId type not support type: $type")
                        null
                    }
                }
            }

            private suspend fun loadInters(adId: AdId): MsAd? {
                val deferred = CompletableDeferred<MsAd?>()
                withContext(Dispatchers.Main) {
                    InterstitialAd.load(
                        ms,
                        adId.id,
                        AdRequest.Builder().build(),
                        object : InterstitialAdLoadCallback() {
                            override fun onAdLoaded(p0: InterstitialAd) {
                                super.onAdLoaded(p0)
                                Logger.logDebugI("AdManager", "load: load InterstitialAd success")
                                deferred.complete(MsAd(adId, p0, type))
                            }

                            override fun onAdFailedToLoad(p0: LoadAdError) {
                                super.onAdFailedToLoad(p0)
                                Logger.logDebugI(
                                    "AdManager",
                                    "load: load InterstitialAd fail msg: ${p0.message}"
                                )
                                deferred.complete(null)
                            }
                        })
                }
                return deferred.await()
            }

            private suspend fun loadOpen(adId: AdId): MsAd? {
                val deferred = CompletableDeferred<MsAd?>()
                withContext(Dispatchers.Main) {
                    AppOpenAd.load(
                        ms,
                        adId.id,
                        AdRequest.Builder().build(),
                        object : AppOpenAd.AppOpenAdLoadCallback() {
                            override fun onAdLoaded(p0: AppOpenAd) {
                                super.onAdLoaded(p0)
                                Logger.logDebugI("AdManager", "load: load AppOpenAd success")
                                deferred.complete(MsAd(adId, p0, type))
                            }

                            override fun onAdFailedToLoad(p0: LoadAdError) {
                                super.onAdFailedToLoad(p0)
                                Logger.logDebugI("AdManager", "load: load AppOpenAd fail msg: ${p0.message}")
                                deferred.complete(null)
                            }
                        })
                }
                return deferred.await()
            }

            private suspend fun loadNative(adId: AdId): MsAd? {
                val deferred = CompletableDeferred<MsAd?>()
                withContext(Dispatchers.Main) {
                    var ad: MsAd? = null
                    AdLoader.Builder(ms, adId.id).forNativeAd {
                        Logger.logDebugI("AdManager", "load: load NativeAd success")
                        ad = MsAd(adId, it, type)
                        deferred.complete(ad)
                    }.withAdListener(object : AdListener() {
                        override fun onAdFailedToLoad(p0: LoadAdError) {
                            super.onAdFailedToLoad(p0)
                            Logger.logDebugI("AdManager", "load: load NativeAd fail msg: ${p0.message}")
                            deferred.complete(null)
                        }

                        override fun onAdClicked() {
                            super.onAdClicked()
                            ad?.clickAd()
                        }
                    }).withNativeAdOptions(NativeAdOptions.Builder().build()).build()
                        .loadAd(AdRequest.Builder().build())
                }
                return deferred.await()
            }

            private fun checkLoad(load: (LoadConfig) -> Unit, callback: (() -> Unit)?) {
                val config = createLoadConfig()
                if (config == null) {
                    Logger.logDebugI("AdManager", "load: config is null type: $type")
                    callback?.invoke()
                } else {
                    if (adSize + loadingCount >= config.limit) {
                        Logger.logDebugI("AdManager", "load: ad is loading type: $type")
                        callback?.let {
                            this.finishCallback?.invoke()
                            this.finishCallback = it
                        }
                    } else {
                        load.invoke(config)
                    }
                }
            }

            private fun createLoadConfig(): LoadConfig? {
                if (loadConfig != null) return loadConfig
                val config = FirebaseHelper.remoteConfig.adInfoConfig
                if (config.isEmpty()) return null
                try {
                    val json = JSONObject(
                        String(
                            Base64.decode(
                                config,
                                Base64.NO_WRAP
                            )
                        )
                    ).getJSONObject(type)
                    val limit = json.getInt("ms_limit")
                    if (limit <= 0) return null
                    val array = json.getJSONArray("ms_id_list")
                    if (array.length() <= 0) return null
                    val list = mutableListOf<AdId>()
                    for (i in 0 until array.length()) {
                        val jsonObject = array.getJSONObject(i)
                        val id = jsonObject.getString("ms_id")
                        val type = jsonObject.optString("ms_type", type)
                        val priority = jsonObject.getInt("ms_priority")
                        list.add(AdId(id, type, priority))
                    }
                    list.sortByDescending { it.priority }
                    loadConfig = LoadConfig(list, limit)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return loadConfig
            }

            fun getProAd(): MsAd? {
                synchronized(proAdList) {
                    val iterator = proAdList.iterator()
                    while (iterator.hasNext()) {
                        val proAd = iterator.next()
                        if (proAd.isCanUse) {
                            return proAd
                        }
                        iterator.remove()
                    }
                    return null
                }
            }

            private fun addProAd(proAd: MsAd) {
                synchronized(proAdList) {
                    proAdList.add(proAd)
                    proAdList.sortByDescending { it.adId.priority }
                }
            }

            private fun startRetry() {
                failedJob?.cancel()
                failedCount++
                failedJob = scope.launch {
                    val delayTime = (2.0.pow(failedCount) * DateUtils.SECOND_IN_MILLIS).toLong()
                    if (delayTime > 0) {
                        delay(delayTime)
                    }
                    withContext(Dispatchers.Main) {
                        preload()
                    }
                }
            }

            private fun cancelRetry() {
                failedJob?.cancel()
                failedJob = null
                failedCount = 0
            }

            fun resetData() {
                loadConfig = null
            }

        }

    }

}