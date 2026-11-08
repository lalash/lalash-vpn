package com.v2ray.ang.ui.speedtest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StepTopNTest {
    @Test
    fun stepsSnapToMultiplesOfFive() {
        assertEquals(55, stepTopN(56, -5))
        assertEquals(60, stepTopN(56, 5))
        assertEquals(15, stepTopN(10, 5))
        assertEquals(5, stepTopN(10, -5))
    }

    @Test
    fun staysWithinBounds() {
        assertEquals(1, stepTopN(5, -5))
        assertEquals(1, stepTopN(1, -5))
        assertEquals(5, stepTopN(1, 5))
        assertEquals(500, stepTopN(500, 5))
    }
}
