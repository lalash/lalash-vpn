package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SpeedTestDownUrlsTest {
    private val all = SpeedTestSettings.DOWNLOAD_URLS

    @Test
    fun listMatchesTheDesktopTesterOrder() {
        assertEquals(
            listOf(
                "https://cachefly.cachefly.net/100mb.test",
                "https://speed.cloudflare.com/__down?bytes={bytes}",
                "https://proof.ovh.net/files/100Mb.dat",
                "https://fsn1-speed.hetzner.com/100MB.bin",
            ),
            all
        )
    }

    @Test
    fun nothingSelectedUsesTheListInOrder() {
        assertEquals(all, SpeedTestSettings.resolveDownUrls(null))
        assertEquals(all, SpeedTestSettings.resolveDownUrls("  "))
    }

    @Test
    fun selectedUrlGoesFirstAndTheRestAreFallbacks() {
        assertEquals(listOf(all[2], all[0], all[1], all[3]), SpeedTestSettings.resolveDownUrls(all[2]))
    }

    @Test
    fun olderCustomUrlIsKeptFirst() {
        val custom = "https://example.com/big.bin"
        assertEquals(listOf(custom) + all, SpeedTestSettings.resolveDownUrls(custom))
    }

    @Test
    fun defaultTopNIsTen() {
        assertEquals(10, SpeedTestSettings.DEFAULT_TOP_N)
    }
}
