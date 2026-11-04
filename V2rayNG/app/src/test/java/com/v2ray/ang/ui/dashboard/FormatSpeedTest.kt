package com.v2ray.ang.ui.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FormatSpeedTest {

    @Test
    fun idleIsZeroKilobytes() {
        assertEquals("0 KB/s", formatSpeed(0.0))
    }

    @Test
    fun lowTrafficIsShownInKilobytes() {
        assertEquals("250 KB/s", formatSpeed(250.0 * 1024))
        assertEquals("1023 KB/s", formatSpeed(1023.0 * 1024))
    }

    @Test
    fun highTrafficIsShownInMegabytes() {
        assertEquals("1.0 MB/s", formatSpeed(1024.0 * 1024))
        assertEquals("12.5 MB/s", formatSpeed(12.5 * 1024 * 1024))
    }
}
