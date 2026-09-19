package com.m.s.micosaver.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackgroundSceneClassifierTest {
    private val classifier = BackgroundSceneClassifier(2_000L)

    @Test
    fun recentHomeMarkerSuppressesBackground() {
        classifier.mark(SceneNotificationManager.Scene.HOME, 1_000L)

        val result = classifier.consume(2_500L)

        assertEquals(SceneNotificationManager.Scene.HOME, result.first)
        assertEquals(1_000L, result.second)
    }

    @Test
    fun recentAppsMarkerSuppressesBackground() {
        classifier.mark(SceneNotificationManager.Scene.RECENT_APPS, 1_000L)

        val result = classifier.consume(2_500L)

        assertEquals(SceneNotificationManager.Scene.RECENT_APPS, result.first)
    }

    @Test
    fun expiredMarkerFallsBackToBackgroundAndIsConsumed() {
        classifier.mark(SceneNotificationManager.Scene.HOME, 1_000L)

        val first = classifier.consume(3_001L)
        val second = classifier.consume(3_100L)

        assertEquals(SceneNotificationManager.Scene.BACKGROUND, first.first)
        assertNull(first.second)
        assertEquals(SceneNotificationManager.Scene.BACKGROUND, second.first)
    }

    @Test
    fun latestMarkerWins() {
        classifier.mark(SceneNotificationManager.Scene.HOME, 1_000L)
        classifier.mark(SceneNotificationManager.Scene.RECENT_APPS, 1_100L)

        assertEquals(
            SceneNotificationManager.Scene.RECENT_APPS,
            classifier.consume(2_000L).first,
        )
    }
}
