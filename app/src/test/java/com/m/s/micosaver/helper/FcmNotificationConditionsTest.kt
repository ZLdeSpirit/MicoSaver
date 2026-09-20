package com.m.s.micosaver.helper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FcmNotificationConditionsTest {
    @Test
    fun permanentRecommendationRequiresPurchaseBackgroundAndInterval() {
        assertTrue(shouldSendPermanentRecommendation(true, false, true))
        assertFalse(shouldSendPermanentRecommendation(false, false, true))
        assertFalse(shouldSendPermanentRecommendation(true, true, true))
        assertFalse(shouldSendPermanentRecommendation(true, false, false))
    }

    @Test
    fun directFcmGateWaitsForAllConcurrentMessages() {
        val gate = DirectFcmGate()

        assertFalse(gate.isActive)
        assertTrue(gate.begin() == 1)
        assertTrue(gate.begin() == 2)
        assertTrue(gate.isActive)
        assertTrue(gate.finish() == 1)
        assertTrue(gate.isActive)
        assertTrue(gate.finish() == 0)
        assertFalse(gate.isActive)
    }

    @Test
    fun minuteTickGateRunsOnlyOncePerMinute() {
        val gate = MinuteTickGate()

        assertTrue(gate.shouldRun(60_000L))
        assertFalse(gate.shouldRun(61_000L))
        assertFalse(gate.shouldRun(119_999L))
        assertTrue(gate.shouldRun(120_000L))
    }
}
