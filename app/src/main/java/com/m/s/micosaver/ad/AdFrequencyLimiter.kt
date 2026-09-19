package com.m.s.micosaver.ad

import android.util.Base64
import com.m.s.micosaver.firebase.FirebaseHelper
import com.m.s.micosaver.ms
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
)

internal class AdFrequencyController(
    private val configProvider: () -> AdFrequencyConfig,
    private val stateProvider: () -> AdFrequencyState,
    private val stateSaver: (AdFrequencyState) -> Unit,
    private val currentTimeProvider: () -> Long,
) {
    @Synchronized
    fun isLimited(): Boolean {
        val config = configProvider()
        if (config.intervalMinutes <= 0) return false

        val state = activeState(config)
        if (state.startTime <= 0L) return false
        return (config.maxShowCount > 0 && state.showCount >= config.maxShowCount) ||
            (config.maxClickCount > 0 && state.clickCount >= config.maxClickCount)
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
    private val defaultConfig = AdFrequencyConfig()

    private val controller by lazy {
        AdFrequencyController(
            configProvider = ::getConfig,
            stateProvider = {
                val (startTime, showCount, clickCount) = ms.data.getAdFrequencyState()
                AdFrequencyState(startTime, showCount, clickCount)
            },
            stateSaver = {
                ms.data.setAdFrequencyState(it.startTime, it.showCount, it.clickCount)
            },
            currentTimeProvider = System::currentTimeMillis,
        )
    }

    fun isLimited(): Boolean = controller.isLimited()

    fun recordShow() = controller.recordShow()

    fun recordClick() = controller.recordClick()

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
