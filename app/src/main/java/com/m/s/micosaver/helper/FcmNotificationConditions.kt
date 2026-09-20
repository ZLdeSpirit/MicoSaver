package com.m.s.micosaver.helper

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.m.s.micosaver.ad.AdFrequencyLimiter
import com.m.s.micosaver.channel.AppChannelHelper
import com.m.s.micosaver.firebase.FirebaseHelper
import com.m.s.micosaver.ms
import org.json.JSONArray
import java.util.Calendar
import java.util.Locale

object FcmNotificationConditions {
    const val TAG = "FcmCondition"

    fun blockedReason(
        msg: Map<String, String>,
        trackFcmAnalytics: Boolean = false,
    ): String? {
        if (AdFrequencyLimiter.isLimited()) return blocked("ad_frequency_limited")
        if (!hasValidatedNetwork()) return blocked("network_unavailable")
        if (trackFcmAnalytics && ms.isOpenMsg) FirebaseHelper.logEvent("ms_receive_open")
        if (!AppChannelHelper.isPro) return blocked("non_purchase_user")
        if (trackFcmAnalytics) FirebaseHelper.logEvent("ms_receive_pro")
        if (LifecycleHelper.isForeground) return blocked("app_foreground")
        if (trackFcmAnalytics) FirebaseHelper.logEvent("ms_receive_background")
        if (!checkSendTime(msg)) return blocked("send_time")
        if (!checkInstallLimit(msg["install_limit"])) return blocked("install_limit")
        if (!checkCountry(msg["ctr"], msg["ex_ctr"])) return blocked("country")
        if (!checkVersion(msg["ver"])) return blocked("version")
        if (!NotificationIntervalLimiter.canSend(NotificationIntervalLimiter.FCM_PUSH)) {
            return blocked("notice_interval")
        }
        return null
    }

    private fun blocked(reason: String): String {
        Log.i(TAG, "allowed=false reason=$reason")
        return reason
    }

    private fun hasValidatedNetwork(): Boolean {
        val manager = ms.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = manager.activeNetwork?.let(manager::getNetworkCapabilities)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun checkInstallLimit(limit: String?): Boolean {
        return try {
            if (limit.isNullOrEmpty()) return true
            val array = JSONArray(limit)
            if (array.length() < 2) return true
            val minInstallMinutes = if (array.isNull(0)) null else array.getLong(0)
            val maxInstallMinutes = if (array.isNull(1)) null else array.getLong(1)
            val installMinutes = (System.currentTimeMillis() - ms.appInstallTime) / 60_000
            (minInstallMinutes == null || installMinutes >= minInstallMinutes) &&
                (maxInstallMinutes == null || installMinutes < maxInstallMinutes)
        } catch (e: Exception) {
            Log.i(TAG, "invalid install limit", e)
            true
        }
    }

    private fun checkCountry(countriesValue: String?, excludeCountriesValue: String?): Boolean {
        val country = Locale.getDefault().country.lowercase(Locale.US)
        val excludeCountries = parseLowercaseArray(excludeCountriesValue)
        if (!excludeCountries.isNullOrEmpty()) {
            if (excludeCountries.contains("all")) return false
            return !excludeCountries.contains(country)
        }

        val countries = parseLowercaseArray(countriesValue)
        if (countries.isNullOrEmpty()) return true
        return countries.contains("all") || countries.contains(country)
    }

    private fun checkVersion(value: String?): Boolean {
        val versions = parseLowercaseArray(value)
        if (versions.isNullOrEmpty() || versions.contains("all")) return true
        val versionName = try {
            ms.packageManager.getPackageInfo(ms.packageName, 0).versionName.orEmpty()
        } catch (e: Exception) {
            ""
        }
        return versions.contains(versionName.lowercase(Locale.US))
    }

    private fun parseLowercaseArray(value: String?): List<String>? {
        return try {
            if (value.isNullOrEmpty()) return null
            val array = JSONArray(value)
            buildList {
                for (index in 0 until array.length()) {
                    if (!array.isNull(index)) {
                        array.getString(index).lowercase(Locale.US)
                            .takeIf(String::isNotEmpty)
                            ?.let(::add)
                    }
                }
            }
        } catch (e: Exception) {
            Log.i(TAG, "invalid string array", e)
            null
        }
    }

    private fun checkSendTime(msg: Map<String, String>): Boolean {
        val value = msg["date_range"]
        if (value.isNullOrEmpty()) return true
        return try {
            val array = JSONArray(value)
            if (array.length() < 2) return true
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            hour in array.getLong(0)..array.getLong(1)
        } catch (e: Exception) {
            Log.i(TAG, "invalid date range", e)
            true
        }
    }
}

internal fun shouldSendPermanentRecommendation(
    isPurchaseUser: Boolean,
    isForeground: Boolean,
    intervalAllowed: Boolean,
): Boolean = isPurchaseUser && !isForeground && intervalAllowed
