package com.m.s.micosaver.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationIntervalLimiterTest {
    @Test
    fun missingSceneUsesDefaultInterval() {
        assertEquals(
            NoticeIntervalConfig(),
            resolveNoticeIntervalConfig(
                mapOf("home" to NoticeIntervalConfig(defaultIntervalSeconds = 10L)),
                "background",
            ),
        )
    }

    @Test
    fun eachSceneUsesItsOwnInterval() {
        val config = mapOf(
            "home" to NoticeIntervalConfig(defaultIntervalSeconds = 10L),
            "background" to NoticeIntervalConfig(defaultIntervalSeconds = 20L),
        )

        assertEquals(10L, resolveNoticeIntervalConfig(config, "home").defaultIntervalSeconds)
        assertEquals(20L, resolveNoticeIntervalConfig(config, "background").defaultIntervalSeconds)
    }

    @Test
    fun installTimeMustBeStrictlyExceeded() {
        val config = NoticeIntervalConfig(installTimeSeconds = 120L)

        assertFalse(isNoticeInstallTimeAllowed(config, 120L))
        assertTrue(isNoticeInstallTimeAllowed(config, 121L))
        assertTrue(isNoticeInstallTimeAllowed(NoticeIntervalConfig(), 0L))
    }

    @Test
    fun rangeIncludesStartExcludesEndAndTakesPriority() {
        val config = NoticeIntervalConfig(
            defaultIntervalSeconds = 300L,
            installRange = NoticeInstallRange(240L, 480L, 600L),
            installOther = NoticeInstallOther(300L, 400L),
        )

        assertEquals(300L, resolveNoticeIntervalSeconds(config, 239L))
        assertEquals(600L, resolveNoticeIntervalSeconds(config, 240L))
        assertEquals(600L, resolveNoticeIntervalSeconds(config, 400L))
        assertEquals(400L, resolveNoticeIntervalSeconds(config, 480L))
    }

    @Test
    fun installOtherRequiresTimeToBeStrictlyExceeded() {
        val config = NoticeIntervalConfig(
            defaultIntervalSeconds = 300L,
            installOther = NoticeInstallOther(800L, 400L),
        )

        assertEquals(300L, resolveNoticeIntervalSeconds(config, 800L))
        assertEquals(400L, resolveNoticeIntervalSeconds(config, 801L))
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
