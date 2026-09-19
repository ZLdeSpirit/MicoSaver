package com.m.s.micosaver.helper

import com.m.s.micosaver.firebase.FirebaseHelper
import org.junit.Assert.assertEquals
import org.junit.Test

class CircleNoticeConfigTest {
    @Test
    fun nullConfigUsesDefaults() {
        assertEquals(CircleNoticeConfig(15, 4_000L), resolveCircleNoticeConfig(null))
    }

    @Test
    fun validConfigUsesTotalCountAndSeconds() {
        val config = resolveCircleNoticeConfig(
            mapOf(
                FirebaseHelper.remoteConfig.CIRCLE_COUNT to 10L,
                FirebaseHelper.remoteConfig.INTERVAL_TIME to 2L,
            )
        )

        assertEquals(CircleNoticeConfig(10, 2_000L), config)
    }

    @Test
    fun invalidValuesUseRequiredMinimums() {
        val config = resolveCircleNoticeConfig(
            mapOf(
                FirebaseHelper.remoteConfig.CIRCLE_COUNT to 0L,
                FirebaseHelper.remoteConfig.INTERVAL_TIME to 0L,
            )
        )

        assertEquals(CircleNoticeConfig(1, 4_000L), config)
    }
}
