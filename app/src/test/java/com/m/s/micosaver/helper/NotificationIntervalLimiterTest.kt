package com.m.s.micosaver.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationIntervalLimiterTest {
    @Test
    fun missingSceneUsesDefaultInterval() {
        assertEquals(
            DEFAULT_NOTICE_INTERVAL_SECONDS,
            resolveNoticeIntervalSeconds(mapOf("home" to 10L), "background"),
        )
    }

    @Test
    fun eachSceneUsesItsOwnInterval() {
        val config = mapOf("home" to 10L, "background" to 20L)

        assertEquals(10L, resolveNoticeIntervalSeconds(config, "home"))
        assertEquals(20L, resolveNoticeIntervalSeconds(config, "background"))
    }

    @Test
    fun nonPositiveIntervalIsUnlimited() {
        assertTrue(isNoticeIntervalAllowed(1_000L, 1_001L, 0L))
        assertTrue(isNoticeIntervalAllowed(1_000L, 1_001L, -1L))
    }

    @Test
    fun intervalAllowsAtBoundary() {
        assertFalse(isNoticeIntervalAllowed(1_000L, 300_999L, 300L))
        assertTrue(isNoticeIntervalAllowed(1_000L, 301_000L, 300L))
    }
}
