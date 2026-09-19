package com.m.s.micosaver.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaNoticeConfigTest {
    @Test
    fun mediaCircleKeepsConfiguredTotalDuration() {
        assertEquals(30, resolveMediaCircleCount(CircleNoticeConfig(15, 4_000L)))
        assertEquals(5, resolveMediaCircleCount(CircleNoticeConfig(3, 3_000L)))
        assertEquals(1, resolveMediaCircleCount(CircleNoticeConfig(1, 1_000L)))
    }

    @Test
    fun resetPolicyKeepsOneRandomWindowUntilCancellation() {
        val policy = MediaNoticeResetPolicy { 25 }

        assertTrue(policy.shouldReset(1_000L))
        policy.recordCancellation(1_000L)
        assertEquals(25 * 60_000L, policy.currentIntervalMillis)
        assertFalse(policy.shouldReset(25 * 60_000L))
        assertTrue(policy.shouldReset(25 * 60_000L + 1_000L))
    }

    @Test
    fun resetMinutesAreClampedToRequiredRange() {
        val low = MediaNoticeResetPolicy { 1 }
        val high = MediaNoticeResetPolicy { 100 }

        low.recordCancellation(1L)
        high.recordCancellation(1L)

        assertEquals(20 * 60_000L, low.currentIntervalMillis)
        assertEquals(40 * 60_000L, high.currentIntervalMillis)
    }
}
