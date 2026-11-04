package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ConfigSpeedTesterTest {

    /** 100 ms samples of a link that moves [bytesPerTick] per sample after [rampTicks] slow ones. */
    private fun curve(ticks: Int, bytesPerTick: Long, rampTicks: Int = 0, rampBytes: Long = 0): List<Pair<Double, Long>> {
        var total = 0L
        return (1..ticks).map { i ->
            total += if (i <= rampTicks) rampBytes else bytesPerTick
            (i * 0.1) to total
        }
    }

    @Test
    fun `returns nothing for fewer than two samples`() {
        val (avg, peak) = ConfigSpeedTester.slope(listOf(0.1 to 100L), warmup = 0.0)
        assertNull(avg)
        assertNull(peak)
    }

    @Test
    fun `steady link reports its rate`() {
        // 125 000 bytes per 100 ms = 10 Mbps.
        val (avg, peak) = ConfigSpeedTester.slope(curve(100, 125_000), warmup = 2.0)
        assertEquals(10.0, avg!!, 0.01)
        assertEquals(10.0, peak!!, 0.01)
    }

    @Test
    fun `warm-up discards the slow start`() {
        // Two slow seconds, then 10 Mbps. Total-over-time would under-report; the slope must not.
        val samples = curve(100, 125_000, rampTicks = 20, rampBytes = 10_000)
        val (avg, _) = ConfigSpeedTester.slope(samples, warmup = 2.0)
        assertEquals(10.0, avg!!, 0.1)
    }

    @Test
    fun `short run falls back to the whole curve`() {
        // Warm-up longer than the run leaves under 0.8 s of steady state, so everything is used.
        val (avg, _) = ConfigSpeedTester.slope(curve(10, 125_000), warmup = 5.0)
        assertEquals(10.0, avg!!, 0.5)
    }
}
