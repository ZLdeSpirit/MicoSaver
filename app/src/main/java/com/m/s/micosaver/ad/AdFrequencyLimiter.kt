package com.m.s.micosaver.ad

import android.util.Base64
import com.m.s.micosaver.firebase.FirebaseHelper
import com.m.s.micosaver.ms
import com.m.s.micosaver.utils.Logger
import org.json.JSONObject

internal data class AdFrequencyConfig(
    val intervalMinutes: Int = 1440,
    val maxShowCount: Int = 50,
    val maxClickCount: Int = 20,
)

internal data class AdFrequencyState(
    val startTime: Long = 0L,
    val showCount: Int = 0,
    val clickCount: Int = 0,
    val showLimitReported: Boolean = false,
    val clickLimitReported: Boolean = false,
)

internal data class AdFrequencyCheckResult(
    val isLimited: Boolean,
    val reportShowLimit: Boolean = false,
    val reportClickLimit: Boolean = false,
)

internal class AdFrequencyController(
    private val configProvider: () -> AdFrequencyConfig,
    private val stateProvider: () -> AdFrequencyState,
    private val stateSaver: (AdFrequencyState) -> Unit,
    private val currentTimeProvider: () -> Long,
) {
    @Synchronized
    fun isLimited(): Boolean {
        return check().isLimited
    }

    @Synchronized
    fun check(): AdFrequencyCheckResult {
        val config = configProvider()
        if (config.intervalMinutes <= 0) return AdFrequencyCheckResult(false)

        val state = activeState(config)
        if (state.startTime <= 0L) return AdFrequencyCheckResult(false)

        val showLimited = config.maxShowCount > 0 && state.showCount >= config.maxShowCount
        val clickLimited = config.maxClickCount > 0 && state.clickCount >= config.maxClickCount
        val reportShowLimit = showLimited && !state.showLimitReported
        val reportClickLimit = clickLimited && !state.clickLimitReported
        if (reportShowLimit || reportClickLimit) {
            stateSaver(
                state.copy(
                    showLimitReported = state.showLimitReported || reportShowLimit,
                    clickLimitReported = state.clickLimitReported || reportClickLimit,
                )
            )
        }
        return AdFrequencyCheckResult(
            isLimited = showLimited || clickLimited,
            reportShowLimit = reportShowLimit,
            reportClickLimit = reportClickLimit,
        )
    }

    @Synchronized
    fun recordShow() {
        val config = configProvider()
        if (config.intervalMinutes <= 0) return

        val now = currentTimeProvider()
        val state = activeState(config, now)
        stateSaver(
            state.copy(
                startTime = state.startTime.takeIf { it > 0L } ?: now,
                showCount = increment(state.showCount),
            )
        )
    }

    @Synchronized
    fun recordClick() {
        val config = configProvider()
        if (config.intervalMinutes <= 0) return

        val state = activeState(config)
        if (state.startTime <= 0L) return
        stateSaver(state.copy(clickCount = increment(state.clickCount)))
    }

    private fun activeState(
        config: AdFrequencyConfig,
        now: Long = currentTimeProvider(),
    ): AdFrequencyState {
        val state = stateProvider()
        if (state.startTime <= 0L) return state
        val intervalMillis = config.intervalMinutes.toLong() * 60_000L
        if (now >= state.startTime && now - state.startTime < intervalMillis) return state

        return AdFrequencyState().also(stateSaver)
    }

    private fun increment(count: Int): Int {
        return if (count == Int.MAX_VALUE) count else count + 1
    }
}

internal object AdFrequencyLimiter {
    private const val TAG = "AdFrequencyLimit"
    private const val SHOW_LIMIT_EVENT = "ad_show_limit"
    private const val CLICK_LIMIT_EVENT = "ad_click_limit"
    private val defaultConfig = AdFrequencyConfig()

    private val controller by lazy {
        AdFrequencyController(
            configProvider = ::getConfig,
            stateProvider = ms.data::getAdFrequencyState,
            stateSaver = ms.data::setAdFrequencyState,
            currentTimeProvider = System::currentTimeMillis,
        )
    }

    fun isLimited(): Boolean = handleCheck("check")

    fun recordShow() {
        controller.recordShow()
        handleCheck("show")
    }

    fun recordClick() {
        controller.recordClick()
        handleCheck("click")
    }

    private fun handleCheck(action: String): Boolean {
        val result = controller.check()
        if (result.reportShowLimit) FirebaseHelper.logEvent(SHOW_LIMIT_EVENT)
        if (result.reportClickLimit) FirebaseHelper.logEvent(CLICK_LIMIT_EVENT)
        if (action != "check" || result.isLimited) logState(action, result.isLimited)
        return result.isLimited
    }

    private fun logState(action: String, isLimited: Boolean) {
        val config = getConfig()
        val state = ms.data.getAdFrequencyState()
        Logger.logDebugI(
            TAG,
            "action=$action startTime=${state.startTime} " +
                "show=${state.showCount}/${config.maxShowCount} " +
                "click=${state.clickCount}/${config.maxClickCount} " +
                "intervalMinutes=${config.intervalMinutes} limited=$isLimited"
        )
    }

    private fun getConfig(): AdFrequencyConfig {
        return try {
            val encoded = FirebaseHelper.remoteConfig.getAdFrequencyLimit()
            val json = JSONObject(String(Base64.decode(encoded, Base64.NO_WRAP), Charsets.UTF_8))
            AdFrequencyConfig(
                intervalMinutes = json.optInt(
                    "ad_show_click_interval",
                    defaultConfig.intervalMinutes,
                ),
                maxShowCount = json.optInt("ad_max_show_count", defaultConfig.maxShowCount),
                maxClickCount = json.optInt("ad_max_click_count", defaultConfig.maxClickCount),
            )
        } catch (e: Exception) {
            e.printStackTrace()
            defaultConfig
        }
    }
}
