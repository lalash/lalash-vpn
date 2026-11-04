package com.v2ray.ang.ui.speedtest

import com.v2ray.ang.dto.SpeedTestMode
import com.v2ray.ang.dto.SpeedTestResult
import com.v2ray.ang.dto.SpeedTestState
import com.v2ray.ang.ui.dashboard.flagEmoji
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SpeedTestRowComparatorTest {

    private fun row(guid: String, state: SpeedTestState, ping: Long? = null, up: Double? = null, down: Double? = null, jitter: Long? = null) =
        SpeedTestRow(guid, guid, null, SpeedTestResult(guid, state, ping, jitter, up, down))

    @Test
    fun `tested rows come first, best upload first, then testing, queued, skipped, dead`() {
        val rows = listOf(
            row("dead", SpeedTestState.DEAD),
            row("slow", SpeedTestState.DONE, ping = 100, up = 1.0),
            row("queued", SpeedTestState.QUEUED, ping = 50),
            row("testing", SpeedTestState.TESTING, ping = 80),
            row("fast", SpeedTestState.DONE, ping = 300, up = 20.0),
            row("skipped", SpeedTestState.SKIPPED, ping = 90),
        )
        val sorted = rows.sortedWith(SpeedTestViewModel.rowComparator(SpeedTestSort.UPLOAD, SpeedTestMode.UPLOAD_DOWNLOAD))
        assertEquals(listOf("fast", "slow", "testing", "queued", "skipped", "dead"), sorted.map { it.guid })
    }

    @Test
    fun `ping sort puts the lowest ping first and missing pings last`() {
        val rows = listOf(
            row("a", SpeedTestState.DONE, ping = 300),
            row("b", SpeedTestState.DONE, ping = null),
            row("c", SpeedTestState.DONE, ping = 120),
        )
        val sorted = rows.sortedWith(SpeedTestViewModel.rowComparator(SpeedTestSort.PING, SpeedTestMode.UPLOAD_DOWNLOAD))
        assertEquals(listOf("c", "a", "b"), sorted.map { it.guid })
    }

    @Test
    fun `rows waiting for the speed phase are ordered by their phase-one ping`() {
        val rows = listOf(
            row("slow", SpeedTestState.QUEUED, ping = 400, jitter = 30),
            row("unpinged", SpeedTestState.PINGING),
            row("fast", SpeedTestState.QUEUED, ping = 90, jitter = 5),
        )
        val sorted = rows.sortedWith(SpeedTestViewModel.rowComparator(SpeedTestSort.UPLOAD, SpeedTestMode.UPLOAD_DOWNLOAD))
        assertEquals(listOf("fast", "slow", "unpinged"), sorted.map { it.guid })
    }

    @Test
    fun `upload sort falls back to ping in ping-only mode`() {
        val rows = listOf(
            row("a", SpeedTestState.DONE, ping = 300),
            row("b", SpeedTestState.DONE, ping = 100),
        )
        val sorted = rows.sortedWith(SpeedTestViewModel.rowComparator(SpeedTestSort.UPLOAD, SpeedTestMode.PING_ONLY))
        assertEquals(listOf("b", "a"), sorted.map { it.guid })
    }

    @Test
    fun `flag emoji is built from regional indicators and rejects bad codes`() {
        assertEquals("🇳🇱", flagEmoji("nl"))
        assertEquals("", flagEmoji("NLD"))
        assertEquals("", flagEmoji("1A"))
    }
}
