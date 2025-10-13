package com.m.s.micosaver

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import android.util.DisplayMetrics
import androidx.core.content.edit
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsLogger
import com.m.s.micosaver.broadcast.BroadcastHelper
import com.m.s.micosaver.channel.AppChannelHelper
import com.m.s.micosaver.firebase.FirebaseHelper
import java.util.Locale

lateinit var ms: MicoSaver
    private set

class MicoSaver : Application(){
    val data by lazy { AppDataHelper() }

    val languageList: List<Pair<String, String>>
        get() {
            return listOf(
                "en" to "English",
                "pt" to "Português",
                "fr" to "Français",
                "hi" to "हिंदी",
                "es" to "Español",
                "ru" to "Русский",
                "ja" to "日本語",
                "ko" to "한국인"
            )
        }

    val appInstallTime: Long
        get() {
            try {
                return packageManager.getPackageInfo(
                    packageName, PackageManager.GET_ACTIVITIES
                ).firstInstallTime
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return data.openAppTime
        }

    override fun onCreate() {
        super.onCreate()
        ms = this
    }

    fun initFacebook() {
        val (id, token) = FirebaseHelper.remoteConfig.initFacebookInfo ?: return
        if (id.isEmpty()) return
        if (token.isNotEmpty()) {
            try {
                FacebookSdk.setApplicationId(id)
                FacebookSdk.setClientToken(token)
                FacebookSdk.sdkInitialize(this)
                AppEventsLogger.activateApp(this)
                // todo
//                AdHelper.facebookInitSuccess()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        AppChannelHelper.initReyunChannel(id)
    }

    fun setProLanguage(lanCode: String?) {
        if (lanCode.isNullOrEmpty()) return
        val setLanCode = data.languageCode
        if (lanCode == setLanCode) return
        data.languageCode = lanCode
        setProLanguage(this)
        BroadcastHelper.sendBroadcast(BroadcastHelper.ACTION_SWITCH_LANGUAGE)
    }

    fun setProLanguage(context: Context?) {
        if (context == null) return
        val newLocale = getCurrentLanguage()
        setCurrentLocale(context, newLocale)
    }

    private fun getLanguageCode(): String? {
        // 加载程序支持的语言
        val lanArray = languageList
        val phoneLans = getSystemLanguageList()
        for (sysLocale in phoneLans) {
            val sysLanguage = sysLocale.language
            val sysCountry = sysLocale.country.lowercase(Locale.US)
            // 找出最相配（语言和国家都相同）和一般相配（只有语言相同）的语言代码
            var first: String? = null// 最相配语言代码（语言和国家完全匹配）
            var second: String? = null// 一般相配语言代码（语言匹配，国家不匹配）
            for (appLocale in lanArray) {
                if (appLocale.first.startsWith(sysLanguage)) {
                    if (appLocale.first.endsWith(sysCountry)) {
                        first = appLocale.first
                        break
                    } else {
                        second = appLocale.first
                    }
                }
            }
            // 优先使用语言和国家都完全匹配的语言代码，
            // 其次选择语言匹配的语言代码
            val code = first.orEmpty().ifEmpty { second }
            if (!code.isNullOrEmpty()) {
                return code
            }
        }
        return null
    }


    private fun getCurrentLanguage(): Locale {
        var setLanCode = data.languageCode
        if (setLanCode.isNullOrEmpty()) {
            setLanCode = getLanguageCode()
            if (setLanCode.isNullOrEmpty()) return Locale.ENGLISH
            data.languageCode = setLanCode
        }
        val (language, country) = if (setLanCode.contains("_")) {
            setLanCode.split("_".toRegex(), 2).map { it.trim() }
        } else {
            listOf(setLanCode, "")
        }
        return Locale(language, country.uppercase(Locale.US))
    }

    private fun getSystemLanguageList(): MutableList<Locale> {
        val phoneLans = mutableListOf<Locale>()
        val config = Resources.getSystem().configuration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            for (i in 0 until config.locales.size()) {
                phoneLans.add(config.locales[i])
            }
        } else {
            @Suppress("DEPRECATION") phoneLans.add(config.locale)
        }
        return phoneLans
    }


    private fun setCurrentLocale(context: Context?, newLocale: Locale): Context? {
        if (context != null) {
            val res = context.resources
            val config = res.configuration
            config.setLocale(newLocale)

            val dm = res.displayMetrics
            val oldValue = DisplayMetrics()
            oldValue.setTo(dm)

            @Suppress("DEPRECATION") res.updateConfiguration(config, dm)
            res.displayMetrics.setTo(oldValue)
        }
        return context
    }

    inner class AppDataHelper {
        private val data by lazy { getSharedPreferences("ms_app_data", MODE_PRIVATE) }

        var languageCode: String?
            get() {
                return data.getString("ms_lang_code", null)
            }
            set(value) {
                data.edit(commit = true) { putString("ms_lang_code", value) }
            }

        var isFirstOpen: Boolean
            get() {
                return data.getBoolean("ms_is_first_open", true)
            }
            set(value) {
                data.edit(commit = true) { putBoolean("ms_is_first_open", value) }
            }

        var openAppTime: Long
            get() {
                return data.getLong("ms_open_app_time", 0)
            }
            set(value) {
                data.edit(commit = true) { putLong("ms_open_app_time", value) }
            }

        var appMarketChannel: String?
            get() {
                return data.getString("ms_app_market_channel", null)
            }
            set(value) {
                data.edit(commit = true) { putString("ms_app_market_channel", value) }
            }

        var reyunChannel: String?
            get() {
                return data.getString("ms_reyun_channel", null)
            }
            set(value) {
                data.edit(commit = true) { putString("ms_reyun_channel", value) }
            }

        var currentProKey: String
            get() {
                return data.getString("ms_current_drop_key", null).orEmpty()
                    .ifEmpty { "ms_key_dd" }
            }
            set(value) {
                data.edit(commit = true) { putString("ms_current_drop_key", value) }
            }

        fun setAdValue(type: String, value: Double, code: String?, isSet: Boolean = false) {
            val saveValue = if (isSet) {
                value
            } else {
                (data.getString("ms_ad_value_$type", "0.0")?.toDouble() ?: 0.0) + value
            }
            data.edit(commit = true) { putString("ms_ad_value_$type", saveValue.toString()) }
            data.edit(commit = true) { putString("ms_ad_value_code_$type", code) }
        }

        fun getAdValue(type: String): Pair<Double, String?> {
            return (data.getString(
                "ms_ad_value_$type", "0.0"
            )?.toDouble() ?: 0.0) to data.getString("ms_ad_value_code_$type", null)
        }

        var isUploadFacebookValue: Boolean
            get() {
                return data.getBoolean("ms_is_uplo_fb_value", true)
            }
            set(value) {
                data.edit(commit = true) { putBoolean("ms_is_uplo_fb_value", value) }
            }

        var fcmToken: String?
            get() {
                return data.getString("ms_fcm_token", null)
            }
            set(value) {
                data.edit(commit = true) { putString("ms_fcm_token", value) }
            }

        var fcmTokenTime: Long
            get() {
                return data.getLong("ms_fcm_token_time", 0)
            }
            set(value) {
                data.edit(commit = true) { putLong("ms_fcm_token_time", value) }
            }

        var isUploadedClack: Boolean
            get() {
                return data.getBoolean("ms_is_uplo_clack", false)
            }
            set(value) {
                data.edit(commit = true) { putBoolean("ms_is_uplo_clack", value) }
            }

        var firstReceiveMsgTime: Long
            get() {
                return data.getLong("ms_first_rece_msg_time", 0)
            }
            set(value) {
                data.edit(commit = true) { putLong("ms_first_rece_msg_time", value) }
            }
        var msgContentIndex: Int
            get() {
                return data.getInt("ms_msg_content_index", 0)
            }
            set(value) {
                data.edit(commit = true) { putInt("ms_msg_content_index", value) }
            }

        var showOpenMsgTime: Long
            get() {
                return data.getLong("ms_show_open_msg_time", 0)
            }
            set(value) {
                data.edit(commit = true) { putLong("ms_show_open_msg_time", value) }
            }

        var isShowedDefaultGuide: Boolean
            get() {
                return data.getBoolean("ms_is_showed_defa_guide", false)
            }
            set(value) {
                data.edit(commit = true) { putBoolean("ms_is_showed_defa_guide", value) }
            }
    }
}