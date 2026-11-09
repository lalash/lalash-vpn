package com.v2ray.ang.handler

import com.v2ray.ang.dto.CheckGroup
import com.v2ray.ang.dto.CheckItem
import com.v2ray.ang.dto.CheckStatus
import com.v2ray.ang.dto.FailKind
import com.v2ray.ang.dto.NetworkInfo
import com.v2ray.ang.dto.NetworkVerdict
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NetworkDiagnosticsTest {
    private val wifi = NetworkInfo(true, NetworkInfo.Transport.WIFI, true, "192.168.1.1", false)

    private fun rows(group: CheckGroup, ok: Int, total: Int, fail: FailKind? = FailKind.CONNECT_TIMEOUT) =
        (0 until total).map { i ->
            CheckItem("${group.name}$i", group, "x", status = if (i < ok) CheckStatus.OK else CheckStatus.FAIL, fail = if (i < ok) null else fail)
        }

    private fun all(iranSites: Int, iranDns: Int, foreignSites: Int, foreignDns: Int, foreignFail: FailKind? = FailKind.CONNECT_TIMEOUT) =
        listOf(
            CheckItem("gateway", CheckGroup.NETWORK, "gateway", status = CheckStatus.OK),
            CheckItem("system_dns", CheckGroup.NETWORK, "system_dns", status = CheckStatus.OK),
        ) + rows(CheckGroup.IRAN_SITES, iranSites, 6) + rows(CheckGroup.IRAN_DNS, iranDns, 8) +
            rows(CheckGroup.FOREIGN_SITES, foreignSites, 6, foreignFail) + rows(CheckGroup.FOREIGN_DNS, foreignDns, 3)

    @Test
    fun noNetwork() {
        val off = NetworkInfo(false, NetworkInfo.Transport.NONE, false, null, false)
        assertEquals(NetworkVerdict.NO_NETWORK, NetworkDiagnostics.conclude(off, emptyList(), 0, 0).verdict)
    }

    @Test
    fun routerProblemWhenGatewayAndEverythingFails() {
        val items = all(0, 0, 0, 0).map { if (it.id == "gateway") it.copy(status = CheckStatus.FAIL) else it }
        assertEquals(NetworkVerdict.ROUTER_PROBLEM, NetworkDiagnostics.conclude(wifi, items, 0, 0).verdict)
    }

    @Test
    fun noInternetWhenNothingAnswers() {
        assertEquals(NetworkVerdict.NO_INTERNET, NetworkDiagnostics.conclude(wifi, all(0, 0, 0, 0), 0, 0).verdict)
    }

    @Test
    fun phoneDnsBrokenWhenDirectDnsWorks() {
        val items = all(0, 5, 0, 2).map { if (it.id == "system_dns") it.copy(status = CheckStatus.FAIL) else it }
        assertEquals(NetworkVerdict.PHONE_DNS_BROKEN, NetworkDiagnostics.conclude(wifi, items, 0, 0).verdict)
    }

    @Test
    fun internationalCutWithPacketsWithoutReply() {
        val c = NetworkDiagnostics.conclude(wifi, all(6, 8, 0, 0), tx = 20_000, rx = 500)
        assertEquals(NetworkVerdict.INTERNATIONAL_CUT, c.verdict)
        assertTrue(c.udpAbroadBlocked)
        assertTrue(c.packetsWithoutReply)
    }

    @Test
    fun filteredNormalWithSniFiltering() {
        val c = NetworkDiagnostics.conclude(wifi, all(6, 8, 3, 3, FailKind.TLS_BLOCKED), 0, 0)
        assertEquals(NetworkVerdict.FILTERED_NORMAL, c.verdict)
        assertTrue(c.tlsFiltering)
        assertFalse(c.udpAbroadBlocked)
    }

    @Test
    fun allGood() {
        assertEquals(NetworkVerdict.ALL_GOOD, NetworkDiagnostics.conclude(wifi, all(6, 8, 6, 3), 0, 0).verdict)
    }

    @Test
    fun parsesPingOutput() {
        val summary = "3 packets transmitted, 3 received, 0% packet loss, time 602ms\nrtt min/avg/max/mdev = 21.1/24.5/30.2/3.8 ms"
        assertEquals(24.5, NetworkDiagnostics.parsePingAverage(summary))
        assertEquals(15.0, NetworkDiagnostics.parsePingAverage("64 bytes: icmp_seq=1 ttl=57 time=10.0 ms\n64 bytes: time=20.0 ms"))
        assertNull(NetworkDiagnostics.parsePingAverage("3 packets transmitted, 0 received, 100% packet loss"))
    }

    @Test
    fun dnsQueryAndReply() {
        val q = NetworkDiagnostics.buildDnsQuery(0x1234, "www.digikala.com")
        assertEquals(0x12, q[0].toInt() and 0xFF)
        assertEquals(0x34, q[1].toInt() and 0xFF)
        // A reply: same id, response bit, no error, one answer.
        val reply = q.copyOf().also { it[2] = 0x81.toByte(); it[3] = 0x80.toByte(); it[6] = 0; it[7] = 1 }
        assertEquals(1, NetworkDiagnostics.parseDnsAnswerCount(reply, 0x1234))
        assertEquals(0, NetworkDiagnostics.parseDnsAnswerCount(reply, 0x9999))
    }

    @Test
    fun detectsDnsBlockPage() {
        assertTrue(NetworkDiagnostics.isPoisoned("10.10.34.35"))
        assertFalse(NetworkDiagnostics.isPoisoned("142.250.74.4"))
    }
}
