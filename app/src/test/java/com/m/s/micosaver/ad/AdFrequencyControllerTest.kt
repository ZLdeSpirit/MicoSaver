package com.m.s.micosaver.ad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdFrequencyControllerTest {
    private var now = 1_000L
    private var config = AdFrequencyConfig()
    private var state = AdFrequencyState()
    private val controller = AdFrequencyController(
        configProvider = { config },
        stateProvider = { state },
        stateSaver = { state = it },
        currentTimeProvider = { now },
    )

    @Test
    fun reachesShowLimitWithinWindow() {
        config = AdFrequencyConfig(maxShowCount = 2, maxClickCount = 0)

        controller.recordShow()
        assertFalse(controller.isLimited())
        controller.recordShow()

        assertTrue(controller.isLimited())
        assertEquals(1_000L, state.startTime)
        assertEquals(2, state.showCount)
    }

    @Test
    fun reachesClickLimitWithinShowStartedWindow() {
        config = AdFrequencyConfig(maxShowCount = 0, maxClickCount = 2)
        controller.recordShow()

        controller.recordClick()
        assertFalse(controller.isLimited())
        controller.recordClick()

        assertTrue(controller.isLimited())
        assertEquals(2, state.clickCount)
    }

    @Test
    fun expiredWindowResetsAndNextShowStartsNewWindow() {
        config = AdFrequencyConfig(intervalMinutes = 1, maxShowCount = 1)
        controller.recordShow()
        assertTrue(controller.isLimited())

        now += 60_000L
        assertFalse(controller.isLimited())
        assertEquals(AdFrequencyState(), state)

        controller.recordShow()
        assertEquals(now, state.startTime)
        assertEquals(1, state.showCount)
    }

    @Test
    fun nonPositiveIntervalDisablesFrequencyLimit() {
        config = AdFrequencyConfig(intervalMinutes = 0, maxShowCount = 1, maxClickCount = 1)

        controller.recordShow()
        controller.recordClick()

        assertFalse(controller.isLimited())
        assertEquals(AdFrequencyState(), state)
    }

    @Test
    fun nonPositiveIndividualLimitsAreUnlimited() {
        config = AdFrequencyConfig(maxShowCount = 0, maxClickCount = -1)
        repeat(100) {
            controller.recordShow()
            controller.recordClick()
        }

        assertFalse(controller.isLimited())
        assertEquals(100, state.showCount)
        assertEquals(100, state.clickCount)
    }
}
