package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MainSortTest {

    private fun row(guid: String, name: String, ping: Long, jitter: Long? = null, down: Double? = null, up: Double? = null) =
        ServerRowUiModel(
            guid = guid,
            profile = ProfileItem.create(EConfigType.VLESS),
            remarks = name,
            statistics = "",
            typeDescription = "VLESS",
            testDelayMillis = ping,
            subscriptionBadge = "",
            jitterMillis = jitter,
            downloadMbps = down,
            uploadMbps = up,
        )

    private val rows = listOf(
        row("a", "Charlie", ping = 300, jitter = 20, down = 10.0, up = 2.0),
        row("b", "alpha", ping = -1),
        row("c", "Bravo", ping = 120, jitter = 5, down = 40.0, up = 1.0),
        row("d", "delta", ping = 0),
    )

    @Test
    fun `subscription order keeps the stored order`() {
        assertEquals(listOf("a", "b", "c", "d"), sortServerRows(rows, MainSort.SUBSCRIPTION).map { it.guid })
    }

    @Test
    fun `ping puts the lowest first and failed or untested last`() {
        assertEquals(listOf("c", "a"), sortServerRows(rows, MainSort.PING).map { it.guid }.take(2))
    }

    @Test
    fun `speed sorts put the fastest first and untested last`() {
        assertEquals(listOf("c", "a", "b", "d"), sortServerRows(rows, MainSort.DOWNLOAD).map { it.guid })
        assertEquals(listOf("a", "c", "b", "d"), sortServerRows(rows, MainSort.UPLOAD).map { it.guid })
    }

    @Test
    fun `jitter and name sorts`() {
        assertEquals(listOf("c", "a"), sortServerRows(rows, MainSort.JITTER).map { it.guid }.take(2))
        assertEquals(listOf("b", "c", "a", "d"), sortServerRows(rows, MainSort.NAME).map { it.guid })
    }

    @Test
    fun `cycling the sort wraps around`() {
        assertEquals(MainSort.PING, MainSort.SUBSCRIPTION.next())
        assertEquals(MainSort.SUBSCRIPTION, MainSort.NAME.next())
    }
}
