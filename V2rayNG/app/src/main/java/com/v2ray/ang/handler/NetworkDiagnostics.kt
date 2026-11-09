package com.v2ray.ang.handler

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Process
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.CheckGroup
import com.v2ray.ang.dto.CheckItem
import com.v2ray.ang.dto.CheckStatus
import com.v2ray.ang.dto.FailKind
import com.v2ray.ang.dto.NetworkConclusion
import com.v2ray.ang.dto.NetworkInfo
import com.v2ray.ang.dto.NetworkVerdict
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.random.Random

/**
 * Checks the phone's own internet, independent of any config: the network itself, the router,
 * DNS, Iranian sites and DNS servers, and foreign sites and DNS servers, then concludes where the
 * problem is. The app is excluded from its own VPN, so these sockets use the raw connection even
 * while connected.
 */
class NetworkDiagnostics(private val context: Context) {

    data class Target(val id: String, val label: String, val host: String)

    /** Runs every check; [onUpdate] receives each row as it finishes (any thread). */
    suspend fun run(onUpdate: (CheckItem) -> Unit): Pair<List<CheckItem>, NetworkConclusion> = withContext(Dispatchers.IO) {
        val results = java.util.Collections.synchronizedList(ArrayList<CheckItem>())
        fun publish(item: CheckItem) {
            results.removeAll { it.id == item.id }
            results.add(item)
            onUpdate(item)
        }

        val info = networkInfo()
        publish(networkRow(info))
        if (!info.connected) {
            val conclusion = NetworkConclusion(NetworkVerdict.NO_NETWORK)
            return@withContext results.toList() to conclusion
        }

        coroutineScope {
            val gateway = async { info.gateway?.let { publish(gatewayRow(it)) } }
            val systemDns = async { publish(systemDnsRow()) }
            val iranSites = IRAN_SITES.map { t -> async { publish(siteRow(t, CheckGroup.IRAN_SITES)) } }
            val iranDns = IRAN_DNS.map { t -> async { publish(dnsServerRow(t, CheckGroup.IRAN_DNS)) } }
            (listOf(gateway, systemDns) + iranSites + iranDns).awaitAll()
        }

        // Foreign checks separately, with traffic counters to see whether replies come back.
        val uid = Process.myUid()
        val tx0 = TrafficStats.getUidTxBytes(uid)
        val rx0 = TrafficStats.getUidRxBytes(uid)
        coroutineScope {
            (FOREIGN_SITES.map { t -> async { publish(siteRow(t, CheckGroup.FOREIGN_SITES)) } } +
                FOREIGN_DNS.map { t -> async { publish(dnsServerRow(t, CheckGroup.FOREIGN_DNS)) } }).awaitAll()
        }
        val tx = (TrafficStats.getUidTxBytes(uid) - tx0).coerceAtLeast(0)
        val rx = (TrafficStats.getUidRxBytes(uid) - rx0).coerceAtLeast(0)

        val all = results.toList()
        all to conclude(info, all, tx, rx)
    }

    // ---------- network ----------

    fun networkInfo(): NetworkInfo {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val network = cm?.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        if (caps == null) return NetworkInfo(false, NetworkInfo.Transport.NONE, false, null, false)
        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkInfo.Transport.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkInfo.Transport.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkInfo.Transport.ETHERNET
            else -> NetworkInfo.Transport.OTHER
        }
        val gateway = cm.getLinkProperties(network)?.routes
            ?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address && !it.gateway!!.isAnyLocalAddress }
            ?.gateway?.hostAddress
        @Suppress("DEPRECATION") // allNetworks: the callback API needs a registration; one snapshot is enough here.
        val vpnActive = cm.allNetworks.any { n ->
            cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
        return NetworkInfo(
            connected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            transport = transport,
            validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            gateway = if (transport == NetworkInfo.Transport.WIFI || transport == NetworkInfo.Transport.ETHERNET) gateway else null,
            vpnActive = vpnActive,
        )
    }

    private fun networkRow(info: NetworkInfo) = CheckItem(
        id = "network",
        group = CheckGroup.NETWORK,
        label = "network",
        detail = info.transport.name + if (info.vpnActive) "+VPN" else "",
        status = when {
            !info.connected -> CheckStatus.FAIL
            !info.validated -> CheckStatus.WARN
            else -> CheckStatus.OK
        },
    )

    private fun gatewayRow(gateway: String): CheckItem {
        val (ms, byTcp) = ping(gateway, tcpPort = 80)
        return CheckItem(
            id = "gateway", group = CheckGroup.NETWORK, label = "gateway", detail = gateway,
            status = if (ms != null) CheckStatus.OK else CheckStatus.FAIL,
            pingMs = ms, pingByTcp = byTcp, fail = if (ms == null) FailKind.NO_PING else null,
        )
    }

    private fun systemDnsRow(): CheckItem {
        val start = System.nanoTime()
        val iran = resolve("www.digikala.com")
        val foreign = resolve("www.google.com")
        val ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
        val poisoned = foreign != null && isPoisoned(foreign)
        return CheckItem(
            id = "system_dns", group = CheckGroup.NETWORK, label = "system_dns",
            detail = listOfNotNull(iran, foreign).joinToString(" · "),
            status = when {
                iran == null && foreign == null -> CheckStatus.FAIL
                poisoned || iran == null || foreign == null -> CheckStatus.WARN
                else -> CheckStatus.OK
            },
            timeMs = ms,
            fail = when {
                iran == null && foreign == null -> FailKind.DNS_FAILED
                poisoned -> FailKind.DNS_POISONED
                else -> null
            },
        )
    }

    // ---------- sites ----------

    /** Resolves [target], pings it and opens an HTTPS connection with its name (SNI). */
    private fun siteRow(target: Target, group: CheckGroup): CheckItem {
        val base = CheckItem(target.id, group, target.label, target.host)
        val ip = if (target.host.first().isDigit()) target.host else resolve(target.host)
            ?: return base.copy(status = CheckStatus.FAIL, fail = FailKind.DNS_FAILED)
        if (isPoisoned(ip)) {
            return base.copy(detail = ip, status = CheckStatus.FAIL, fail = FailKind.DNS_POISONED)
        }
        val (ping, byTcp) = ping(ip, tcpPort = null)
        val https = https(target.host, ip)
        val status = when {
            https.first != null -> CheckStatus.OK
            ping != null -> CheckStatus.WARN
            else -> CheckStatus.FAIL
        }
        return base.copy(detail = ip, status = status, pingMs = ping, pingByTcp = byTcp, timeMs = https.first, fail = https.second)
    }

    /** (milliseconds to an HTTP status line, or null) and the failure kind. */
    private fun https(host: String, ip: String): Pair<Long?, FailKind?> {
        val start = System.nanoTime()
        val raw = Socket()
        try {
            try {
                raw.connect(InetSocketAddress(ip, 443), CONNECT_TIMEOUT_MS)
            } catch (_: SocketTimeoutException) {
                return null to FailKind.CONNECT_TIMEOUT
            } catch (_: ConnectException) {
                return null to FailKind.CONNECT_REFUSED
            }
            raw.soTimeout = CONNECT_TIMEOUT_MS
            val name = if (host.first().isDigit()) ip else host
            val tls = try {
                ((SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(raw, name, 443, true) as SSLSocket).apply { startHandshake() }
            } catch (e: Exception) {
                // A reset or silence right after the server name is sent is how SNI filtering looks.
                LogUtil.w(AppConfig.TAG, "NetworkCheck: TLS to $host failed: ${e.message}")
                return null to FailKind.TLS_BLOCKED
            }
            tls.use { s ->
                s.outputStream.write("HEAD / HTTP/1.1\r\nHost: $name\r\nUser-Agent: lalash-vpn\r\nConnection: close\r\n\r\n".toByteArray())
                s.outputStream.flush()
                val line = s.inputStream.bufferedReader().readLine().orEmpty()
                return if (line.startsWith("HTTP/")) {
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) to null
                } else {
                    null to FailKind.HTTP_ERROR
                }
            }
        } catch (e: Exception) {
            return null to FailKind.HTTP_ERROR
        } finally {
            runCatching { raw.close() }
        }
    }

    // ---------- DNS servers ----------

    /** Pings a DNS server and asks it for an address over UDP port 53. */
    private fun dnsServerRow(target: Target, group: CheckGroup): CheckItem {
        val (ping, byTcp) = ping(target.host, tcpPort = null)
        val answerMs = dnsQuery(target.host, DNS_TEST_NAME)
        val status = when {
            answerMs != null -> CheckStatus.OK
            ping != null -> CheckStatus.WARN
            else -> CheckStatus.FAIL
        }
        return CheckItem(
            target.id, group, target.label, target.host, status, ping, byTcp, answerMs,
            fail = if (answerMs == null) FailKind.NO_DNS_ANSWER else null,
        )
    }

    /** Milliseconds until [server] answered an A query for [name] with at least one record, or null. */
    private fun dnsQuery(server: String, name: String): Long? = try {
        DatagramSocket().use { socket ->
            socket.soTimeout = DNS_TIMEOUT_MS
            val id = Random.nextInt(0, 0xFFFF)
            val query = buildDnsQuery(id, name)
            val start = System.nanoTime()
            socket.send(DatagramPacket(query, query.size, InetAddress.getByName(server), 53))
            val buf = ByteArray(512)
            val packet = DatagramPacket(buf, buf.size)
            socket.receive(packet)
            val ok = packet.length >= 12 && parseDnsAnswerCount(buf, id) > 0
            if (ok) TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) else null
        }
    } catch (_: Exception) {
        null
    }

    // ---------- helpers ----------

    private fun resolve(host: String): String? = try {
        InetAddress.getAllByName(host).firstOrNull { it is Inet4Address }?.hostAddress
    } catch (_: Exception) {
        null
    }

    /**
     * ICMP ping through the system `ping` tool (allowed for apps on Android); when that is not
     * available or gets no reply and [tcpPort] is given, the TCP connect time is used instead.
     * Returns (milliseconds or null, measured by TCP).
     */
    private fun ping(ip: String, tcpPort: Int?): Pair<Long?, Boolean> {
        icmpPing(ip)?.let { return it to false }
        val port = tcpPort ?: return null to false
        return tcpConnectMs(ip, port) to true
    }

    private fun icmpPing(ip: String): Long? = try {
        val process = ProcessBuilder("/system/bin/ping", "-c", "3", "-i", "0.3", "-W", "2", ip)
            .redirectErrorStream(true).start()
        val output = InputStreamReader(process.inputStream).readText()
        process.waitFor(8, TimeUnit.SECONDS)
        parsePingAverage(output)?.toLong()
    } catch (_: Exception) {
        null
    }

    private fun tcpConnectMs(ip: String, port: Int): Long? = try {
        Socket().use { s ->
            val start = System.nanoTime()
            s.connect(InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS)
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
        }
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 4000
        private const val DNS_TIMEOUT_MS = 2500
        private const val DNS_TEST_NAME = "www.digikala.com"

        val IRAN_SITES = listOf(
            Target("ir_aparat", "Aparat", "www.aparat.com"),
            Target("ir_digikala", "Digikala", "www.digikala.com"),
            Target("ir_divar", "Divar", "divar.ir"),
            Target("ir_varzesh3", "Varzesh3", "www.varzesh3.com"),
            Target("ir_snapp", "Snapp", "snapp.ir"),
            Target("ir_irna", "IRNA", "www.irna.ir"),
        )

        val IRAN_DNS = listOf(
            Target("dns_shecan1", "Shecan", "178.22.122.100"),
            Target("dns_shecan2", "Shecan", "185.51.200.2"),
            Target("dns_electro1", "Electro", "78.157.42.100"),
            Target("dns_electro2", "Electro", "78.157.42.101"),
            Target("dns_radar1", "Radar Game", "10.202.10.10"),
            Target("dns_radar2", "Radar Game", "10.202.10.11"),
            Target("dns_begzar", "Begzar", "185.55.226.26"),
            Target("dns_403", "403.online", "10.202.10.202"),
        )

        val FOREIGN_SITES = listOf(
            Target("fx_google", "Google", "www.google.com"),
            Target("fx_cloudflare", "Cloudflare", "www.cloudflare.com"),
            Target("fx_github", "GitHub", "github.com"),
            Target("fx_wikipedia", "Wikipedia", "www.wikipedia.org"),
            Target("fx_telegram", "Telegram", "telegram.org"),
            Target("fx_1111", "1.1.1.1", "1.1.1.1"),
        )

        val FOREIGN_DNS = listOf(
            Target("fdns_cloudflare", "Cloudflare", "1.1.1.1"),
            Target("fdns_google", "Google", "8.8.8.8"),
            Target("fdns_quad9", "Quad9", "9.9.9.9"),
        )

        /** Iran's DNS-level block page answers blocked names with these private addresses. */
        fun isPoisoned(ip: String): Boolean = ip.startsWith("10.10.34.")

        /** Average round trip from `ping` output ("rtt min/avg/max/mdev = 1/2/3/4 ms"), else null. */
        fun parsePingAverage(output: String): Double? {
            Regex("""=\s*[\d.]+/([\d.]+)/[\d.]+""").find(output)?.let { return it.groupValues[1].toDoubleOrNull() }
            val times = Regex("""time[=<]([\d.]+)""").findAll(output).mapNotNull { it.groupValues[1].toDoubleOrNull() }.toList()
            return times.takeIf { it.isNotEmpty() }?.average()
        }

        fun buildDnsQuery(id: Int, name: String): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            out.write(id shr 8 and 0xFF); out.write(id and 0xFF)
            out.write(0x01); out.write(0x00) // standard query, recursion desired
            out.write(0); out.write(1) // QDCOUNT
            repeat(6) { out.write(0) } // ANCOUNT, NSCOUNT, ARCOUNT
            name.trimEnd('.').split('.').forEach { label ->
                val bytes = label.toByteArray()
                out.write(bytes.size); out.write(bytes)
            }
            out.write(0)
            out.write(0); out.write(1) // type A
            out.write(0); out.write(1) // class IN
            return out.toByteArray()
        }

        /** Answer count of a reply to query [id] with no error, or 0. */
        fun parseDnsAnswerCount(reply: ByteArray, id: Int): Int {
            if (reply.size < 12) return 0
            val replyId = (reply[0].toInt() and 0xFF shl 8) or (reply[1].toInt() and 0xFF)
            val isResponse = reply[2].toInt() and 0x80 != 0
            val rcode = reply[3].toInt() and 0x0F
            if (replyId != id || !isResponse || rcode != 0) return 0
            return (reply[6].toInt() and 0xFF shl 8) or (reply[7].toInt() and 0xFF)
        }

        /**
         * Where the problem is, from the finished rows. [tx]/[rx] are this app's bytes sent and
         * received during the foreign checks.
         */
        fun conclude(info: NetworkInfo, items: List<CheckItem>, tx: Long, rx: Long): NetworkConclusion {
            fun ok(group: CheckGroup) = items.count { it.group == group && it.status == CheckStatus.OK }
            fun total(group: CheckGroup) = items.count { it.group == group }
            val gatewayFailed = items.firstOrNull { it.id == "gateway" }?.status == CheckStatus.FAIL
            val systemDnsFailed = items.firstOrNull { it.id == "system_dns" }?.status == CheckStatus.FAIL
            val iranSites = ok(CheckGroup.IRAN_SITES)
            val iranDns = ok(CheckGroup.IRAN_DNS)
            val foreignSites = ok(CheckGroup.FOREIGN_SITES)
            val foreignDns = ok(CheckGroup.FOREIGN_DNS)
            val foreign = items.filter { it.group == CheckGroup.FOREIGN_SITES }
            val timeouts = foreign.count { it.fail == FailKind.CONNECT_TIMEOUT }

            val verdict = when {
                !info.connected -> NetworkVerdict.NO_NETWORK
                gatewayFailed && iranSites == 0 && foreignSites == 0 -> NetworkVerdict.ROUTER_PROBLEM
                iranSites == 0 && iranDns == 0 && foreignSites == 0 && foreignDns == 0 -> NetworkVerdict.NO_INTERNET
                systemDnsFailed && (iranDns > 0 || foreignDns > 0) -> NetworkVerdict.PHONE_DNS_BROKEN
                iranSites > 0 && foreignSites == 0 -> NetworkVerdict.INTERNATIONAL_CUT
                foreignSites < total(CheckGroup.FOREIGN_SITES) -> NetworkVerdict.FILTERED_NORMAL
                else -> NetworkVerdict.ALL_GOOD
            }
            return NetworkConclusion(
                verdict = verdict,
                dnsPoisoned = items.any { it.fail == FailKind.DNS_POISONED },
                tlsFiltering = foreign.any { it.fail == FailKind.TLS_BLOCKED },
                udpAbroadBlocked = total(CheckGroup.FOREIGN_DNS) > 0 && foreignDns == 0 && (iranDns > 0 || iranSites > 0),
                packetsWithoutReply = foreignSites == 0 && timeouts > 0 && tx > 2 * 1024 && rx < tx / 4,
            )
        }
    }
}
