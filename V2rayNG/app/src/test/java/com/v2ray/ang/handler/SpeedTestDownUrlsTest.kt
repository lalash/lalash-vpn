package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SpeedTestDownUrlsTest {
    private val all = SpeedTestSettings.DOWNLOAD_SERVERS.map { it.url }

    @Test
    fun defaultsUseEveryBuiltInServerInOrder() {
        assertEquals(all, SpeedTestSettings.resolveDownUrls(null, null))
        assertEquals(all, SpeedTestSettings.resolveDownUrls("  ", ""))
    }

    @Test
    fun customUrlIsTriedFirstAndNotDuplicated() {
        val custom = "https://example.com/big.bin"
        assertEquals(listOf(custom) + all, SpeedTestSettings.resolveDownUrls(custom, null))
        assertEquals(listOf(all[1]) + (all - all[1]), SpeedTestSettings.resolveDownUrls(all[1], null))
    }

    @Test
    fun retiredDefaultIsIgnored() {
        assertEquals(all, SpeedTestSettings.resolveDownUrls(AppConfig.ST_DEAD_DOWN_URL, null))
    }

    @Test
    fun switchedOffServersAreSkipped() {
        val off = SpeedTestSettings.DOWNLOAD_SERVERS.take(2).joinToString(",") { it.key }
        assertEquals(all.drop(2), SpeedTestSettings.resolveDownUrls(null, off))
    }

    @Test
    fun allSwitchedOffFallsBackToEveryServer() {
        val off = SpeedTestSettings.DOWNLOAD_SERVERS.joinToString(",") { it.key }
        assertEquals(all, SpeedTestSettings.resolveDownUrls(null, off))
    }
}
