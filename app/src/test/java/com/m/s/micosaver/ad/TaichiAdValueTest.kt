package com.m.s.micosaver.ad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaichiAdValueTest {
    @Test
    fun valueBelowThresholdKeepsAccumulating() {
        val result = AdHelper.accumulateTaichiAdValue(0.01, 0.01, 0.03)

        assertEquals(0.02, result.storedValue, 0.0)
        assertNull(result.reportValue)
    }

    @Test
    fun valueAtThresholdReportsAndResetsOnlyThatAccumulator() {
        val result = AdHelper.accumulateTaichiAdValue(0.02, 0.01, 0.03)

        assertEquals(0.0, result.storedValue, 0.0)
        assertEquals(0.03, result.reportValue!!, 0.000000001)
    }

    @Test
    fun reportedValueIncludesAmountBeyondThreshold() {
        val result = AdHelper.accumulateTaichiAdValue(0.04, 0.02, 0.05)

        assertEquals(0.0, result.storedValue, 0.0)
        assertEquals(0.06, result.reportValue!!, 0.000000001)
    }
}
