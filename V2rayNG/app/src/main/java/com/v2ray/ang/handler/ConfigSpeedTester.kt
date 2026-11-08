package com.v2ray.ang.handler

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreConfigManager
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.dto.SpeedTestMode
import com.v2ray.ang.dto.SpeedTestRequest
import com.v2ray.ang.dto.SpeedTestResult
import com.v2ray.ang.dto.SpeedTestState
import com.v2ray.ang.dto.SpeedTestUpdate
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import libv2ray.CoreCallbackHandler
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Two-phase config speed test, ported from the desktop "v2ray tester":
 *
 * 1. Latency for every config in parallel (no bandwidth, so parallelism is free): start a
 *    temporary core with a local SOCKS inbound, take the cold ping (connect + TLS + first
 *    byte) and warm round trips on the same connection for jitter. Dead configs drop out here.
 * 2. Bandwidth for the top N by ping, one at a time: download and upload only. Ping and
 *    jitter from phase 1 are kept as they are.
 *
 * Bandwidth tests run sequentially on purpose: two at once split the same line and both
 * report roughly half the real speed.
 *
 * Throughput is the slope of the steady part of a byte-count curve sampled every 100 ms,
 * not total bytes over total time: TCP slow-start and the socket/TLS buffers on upload
 * would otherwise skew slow links badly.
 */
class ConfigSpeedTester(
    private val context: Context,
    private val settings: SpeedTestSettings,
    private val onUpdate: (SpeedTestUpdate) -> Unit,
) {
    private val pingDone = AtomicInteger(0)
    private var pingTotal = 0
    private var speedDone = 0
    private var speedTotal = 0
    @Volatile
    private var currentGuid: String? = null

    /** Index in [SpeedTestSettings.downUrls] of the server that last worked; tried first. */
    @Volatile
    private var preferredDownIndex = 0

    suspend fun run(request: SpeedTestRequest) = withContext(Dispatchers.IO) {
        val guids = request.guids.distinct()
        pingTotal = guids.size
        val pingOnly = request.mode == SpeedTestMode.PING_ONLY

        // ---- Phase 1: ping + jitter for all, in parallel ----
        val latencies = ConcurrentHashMap<String, SpeedTestResult>()
        val gate = Semaphore(settings.pingConcurrency)
        coroutineScope {
            guids.forEach { guid ->
                launch {
                    gate.withPermit {
                        ensureActive()
                        emit(SpeedTestResult(guid = guid, state = SpeedTestState.PINGING))
                        val result = try {
                            measureLatencyOf(guid)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            LogUtil.e(AppConfig.TAG, "SpeedTest: latency failed guid=$guid", e)
                            SpeedTestResult(guid = guid, state = SpeedTestState.DEAD, error = e.message ?: e.javaClass.simpleName)
                        }
                        val final = when {
                            result.state == SpeedTestState.DEAD -> result
                            pingOnly -> result.copy(state = SpeedTestState.DONE)
                            else -> result.copy(state = SpeedTestState.QUEUED)
                        }
                        latencies[guid] = final
                        pingDone.incrementAndGet()
                        emit(final)
                    }
                }
            }
        }
        if (pingOnly) return@withContext

        // ---- Phase 2: download/upload for the top N, one at a time ----
        val alive = guids.filter { latencies[it]?.state == SpeedTestState.QUEUED }
            .sortedBy { latencies[it]?.pingMs ?: Long.MAX_VALUE }
        val dead = guids.filter { latencies[it]?.state == SpeedTestState.DEAD }
        val ordered = if (settings.skipDead) alive else alive + dead
        val candidates = ordered.take(max(1, request.topN))
        speedTotal = candidates.size
        val tested = candidates.toSet()
        alive.filter { it !in tested }.forEach {
            // Alive but outside the top N: keep its ping and jitter, mark as not speed-tested.
            emit(latencies.getValue(it).copy(state = SpeedTestState.SKIPPED))
        }
        emit(null)

        candidates.forEach { guid ->
            ensureActive()
            currentGuid = guid
            val base = latencies[guid] ?: SpeedTestResult(guid = guid, state = SpeedTestState.QUEUED)
            val testing = base.copy(state = SpeedTestState.TESTING, error = null)
            emit(testing)
            val result = try {
                measureBandwidth(testing, request.mode)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "SpeedTest: bandwidth failed guid=$guid", e)
                testing.copy(state = SpeedTestState.DEAD, error = e.message ?: e.javaClass.simpleName)
            }
            speedDone++
            currentGuid = null
            emit(result)
        }
    }

    private fun emit(result: SpeedTestResult?) {
        onUpdate(SpeedTestUpdate(pingDone.get(), pingTotal, speedDone, speedTotal, currentGuid, result))
    }

    // ------------------------------------------------------------------ per config

    /** Runs [block] with a temporary core for [guid] listening as SOCKS on a free local port. */
    private suspend fun <T> withTemporaryCore(guid: String, streams: Int, onError: (String) -> T, block: suspend (OkHttpClient) -> T): T {
        val port = Utils.findRandomFreePort()
        val config = CoreConfigManager.getV2rayConfig4Bandwidth(context, guid, port)
        if (!config.status) return onError(config.errorMessage)
        val controller = CoreNativeManager.newCoreController(NoopCallback)
        try {
            controller.startLoop(config.content, 0)
            if (!controller.isRunning) return onError("core failed to start")
            val client = buildClient(port, streams)
            try {
                return block(client)
            } finally {
                client.dispatcher.cancelAll()
                client.connectionPool.evictAll()
            }
        } finally {
            try {
                controller.stopLoop()
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "SpeedTest: failed to stop temporary core guid=$guid", e)
            }
        }
    }

    /** Phase 1 for one config: ping, jitter, and the exit IP when enabled. */
    private suspend fun measureLatencyOf(guid: String): SpeedTestResult =
        withTemporaryCore(
            guid,
            streams = 1,
            onError = { SpeedTestResult(guid = guid, state = SpeedTestState.DEAD, error = it) },
        ) { client ->
            val latency = measureLatency(client)
            val ping = latency.pingMs
            when {
                ping == null -> SpeedTestResult(guid = guid, state = SpeedTestState.DEAD, error = latency.error)
                settings.maxPingMs > 0 && ping > settings.maxPingMs -> SpeedTestResult(
                    guid = guid, state = SpeedTestState.DEAD, pingMs = ping, jitterMs = latency.jitterMs,
                    error = "ping > ${settings.maxPingMs} ms",
                )
                else -> {
                    val (ip, country) = if (settings.fetchExitIp) fetchExit(client) else null to null
                    SpeedTestResult(
                        guid = guid, state = SpeedTestState.QUEUED,
                        pingMs = ping, jitterMs = latency.jitterMs,
                        exitIp = ip, exitCountry = country,
                    )
                }
            }
        }

    /** Phase 2 for one config: download then upload. Ping and jitter in [base] stay untouched. */
    private suspend fun measureBandwidth(base: SpeedTestResult, mode: SpeedTestMode): SpeedTestResult =
        withTemporaryCore(
            base.guid,
            streams = max(settings.streamsDownload, settings.streamsUpload),
            onError = { base.copy(state = SpeedTestState.DEAD, error = it) },
        ) { client ->
            var partial = base
            if (mode.measuresDownload) {
                val downUrl = pickDownloadUrl(client)
                val down = if (downUrl == null) {
                    Throughput(null, "no download server answered")
                } else {
                    measureThroughput(client, upload = false, downUrl = downUrl) { live -> emit(partial.copy(liveMbps = live)) }
                }
                partial = partial.copy(downloadMbps = down.mbps, error = down.error, liveMbps = null)
                emit(partial)
            }
            if (mode.measuresUpload) {
                val up = measureThroughput(client, upload = true) { live -> emit(partial.copy(liveMbps = live)) }
                partial = partial.copy(uploadMbps = up.mbps, error = up.error ?: partial.error, liveMbps = null)
            }
            val gotAny = partial.downloadMbps != null || partial.uploadMbps != null
            partial.copy(state = if (gotAny) SpeedTestState.DONE else SpeedTestState.DEAD)
        }

    private data class Latency(val pingMs: Long?, val jitterMs: Long?, val error: String?)

    /** Cold request (connect + TLS + first byte), then warm round trips on the same connection. */
    private fun measureLatency(client: OkHttpClient): Latency {
        var ping: Long? = null
        var error: String? = null
        val warm = ArrayList<Double>()
        for (i in 0 until settings.pingProbes) {
            val start = System.nanoTime()
            try {
                client.newCall(Request.Builder().url(settings.pingUrl).build()).execute().use { response ->
                    response.body.bytes()
                    val ms = (System.nanoTime() - start) / 1e6
                    if (response.code >= 400) return@use
                    if (i == 0) ping = ms.toLong() else warm.add(ms)
                }
            } catch (e: Exception) {
                if (i == 0) {
                    error = e.message ?: e.javaClass.simpleName
                    return Latency(null, null, error)
                }
            }
        }
        val jitter = if (warm.size > 1) {
            val mean = warm.average()
            sqrt(warm.sumOf { (it - mean) * (it - mean) } / warm.size).toLong()
        } else null
        return Latency(ping, jitter, if (ping == null) "no successful probe" else null)
    }

    /** Exit IP and location from Cloudflare's key=value trace endpoint. */
    private fun fetchExit(client: OkHttpClient): Pair<String?, String?> {
        try {
            client.newCall(Request.Builder().url(AppConfig.ST_META_URL).build()).execute().use { response ->
                if (response.code >= 400) return null to null
                val data = response.body.string().lines().mapNotNull { line ->
                    val idx = line.indexOf('=')
                    if (idx > 0) line.substring(0, idx).trim() to line.substring(idx + 1).trim() else null
                }.toMap()
                return data["ip"] to data["loc"]
            }
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "SpeedTest: exit IP lookup failed: ${e.javaClass.simpleName}")
            return null to null
        }
    }

    // ------------------------------------------------------------------ throughput

    private data class Sample(val t: Double, val n: Long)
    private data class Throughput(val mbps: Double?, val error: String?)

    /**
     * The first download server that really serves data through this config, starting with the
     * one that worked last. A server that errors, times out or sends almost nothing (a removed
     * test file, a block page) is skipped, so the test does not report 0 for every config.
     */
    private fun pickDownloadUrl(client: OkHttpClient): String? {
        val urls = settings.downUrls
        val probeClient = client.newBuilder().callTimeout(PROBE_TIMEOUT_SEC, TimeUnit.SECONDS).build()
        for (step in urls.indices) {
            val index = (preferredDownIndex + step) % urls.size
            if (probeDownload(probeClient, urls[index])) {
                preferredDownIndex = index
                return urls[index]
            }
        }
        return null
    }

    private fun probeDownload(client: OkHttpClient, url: String): Boolean = try {
        client.newCall(downloadRequest(url)).execute().use { response ->
            if (!response.isSuccessful) return false
            val source = response.body.source()
            val buffer = okio.Buffer()
            var received = 0L
            while (received < PROBE_BYTES) {
                val read = source.read(buffer, 65_536)
                if (read < 0) break
                received += read
                buffer.clear()
            }
            received >= PROBE_BYTES
        }
    } catch (e: Exception) {
        LogUtil.w(AppConfig.TAG, "SpeedTest: download server ${Uri.parse(url).host} failed: ${e.message}")
        false
    }

    private suspend fun measureThroughput(
        client: OkHttpClient,
        upload: Boolean,
        downUrl: String = "",
        onLive: (Double) -> Unit,
    ): Throughput = coroutineScope {
        val duration = if (upload) settings.durationUploadSec else settings.durationDownloadSec
        val warmup = if (upload) settings.warmupUploadSec else settings.warmupDownloadSec
        val streams = if (upload) settings.streamsUpload else settings.streamsDownload
        val counter = AtomicLong(0)
        val stop = AtomicBoolean(false)
        val samples = Collections.synchronizedList(ArrayList<Sample>())
        val calls = Collections.synchronizedList(ArrayList<Call>())
        val errors = Collections.synchronizedList(ArrayList<String>())
        val throttled = AtomicBoolean(false)
        val t0 = System.nanoTime()
        fun now() = (System.nanoTime() - t0) / 1e9

        val workers = (0 until streams).map { index ->
            launch(Dispatchers.IO) {
                // Stagger the openings so the test server sees a ramp, not a burst.
                delay(index * 120L)
                var backoff = 500L
                while (isActive && !stop.get()) {
                    val call = client.newCall(if (upload) uploadRequest(counter, stop) else downloadRequest(downUrl))
                    calls.add(call)
                    try {
                        call.execute().use { response ->
                            if (response.code == 429 || response.code >= 500) {
                                throttled.set(true)
                                return@use
                            }
                            if (response.code >= 400) {
                                errors.add("HTTP ${response.code}")
                                stop.set(true)
                                return@use
                            }
                            backoff = 500L
                            if (!upload) {
                                val source = response.body.source()
                                val buffer = okio.Buffer()
                                while (!stop.get()) {
                                    val read = source.read(buffer, 65_536)
                                    if (read < 0) break
                                    counter.addAndGet(read)
                                    buffer.clear()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        if (!stop.get()) errors.add(e.message ?: e.javaClass.simpleName)
                    } finally {
                        calls.remove(call)
                    }
                    if (throttled.get() && !stop.get()) {
                        delay(backoff)
                        backoff = min(backoff * 2, 4000L)
                    } else if (errors.isNotEmpty() && !stop.get()) {
                        delay(backoff)
                    }
                }
            }
        }

        var lastLive = 0.0
        while (now() < duration && workers.any { it.isActive }) {
            delay(100)
            samples.add(Sample(now(), counter.get()))
            if (now() - lastLive >= 0.5) {
                lastLive = now()
                liveMbps(samples)?.let(onLive)
            }
        }
        stop.set(true)
        samples.add(Sample(now(), counter.get()))
        synchronized(calls) { calls.forEach { it.cancel() } }
        workers.forEach { it.cancel() }

        val (avg, _) = slope(samples.toList(), warmup)
        val total = counter.get()
        val limited = if (throttled.get()) "test server rate-limited (429/5xx)" else null
        when {
            total == 0L -> Throughput(null, limited ?: errors.firstOrNull() ?: if (upload) "no data sent" else "no data received")
            else -> Throughput(avg, limited)
        }
    }

    private fun downloadRequest(downUrl: String): Request {
        val bytes = settings.downloadSizeMb.toLong() * 1024 * 1024
        val url = downUrl.replace("{bytes}", bytes.toString())
        return Request.Builder().url(url)
            .header("Accept-Encoding", "identity")
            .header("Cache-Control", "no-cache")
            .build()
    }

    private fun uploadRequest(counter: AtomicLong, stop: AtomicBoolean): Request {
        val body = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = -1L
            override fun writeTo(sink: BufferedSink) {
                while (!stop.get()) {
                    sink.write(UPLOAD_CHUNK)
                    sink.flush()
                    counter.addAndGet(UPLOAD_CHUNK.size.toLong())
                }
            }
        }
        return Request.Builder().url(settings.upUrl).post(body).build()
    }

    private fun liveMbps(samples: List<Sample>): Double? {
        val snapshot = synchronized(samples) { samples.toList() }
        if (snapshot.size < 2) return null
        val last = snapshot.last()
        val first = snapshot.lastOrNull { last.t - it.t >= 1.0 } ?: snapshot.first()
        val dt = last.t - first.t
        return if (dt > 0) (last.n - first.n) * 8 / dt / 1e6 else null
    }

    private fun buildClient(port: Int, streams: Int): OkHttpClient {
        val timeout = (settings.requestTimeoutSec * 1000).toLong()
        val builder = OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress(AppConfig.LOOPBACK, port)))
            .connectTimeout(timeout, TimeUnit.MILLISECONDS)
            .readTimeout(timeout, TimeUnit.MILLISECONDS)
            .writeTimeout(timeout, TimeUnit.MILLISECONDS)
            .followRedirects(false)
            .retryOnConnectionFailure(false)
            .connectionPool(okhttp3.ConnectionPool(streams + 2, 30, TimeUnit.SECONDS))
        builder.dispatcher(okhttp3.Dispatcher().apply {
            maxRequests = streams + 4
            maxRequestsPerHost = streams + 4
        })
        if (!settings.verifyTls) applyInsecureTls(builder)
        return builder.build()
    }

    /** Only used when the user turns off "verify TLS certificate" in speed test settings. */
    @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
    private fun applyInsecureTls(builder: OkHttpClient.Builder) {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAll), SecureRandom()) }
        builder.sslSocketFactory(ssl.socketFactory, trustAll).hostnameVerifier { _, _ -> true }
    }

    private object NoopCallback : CoreCallbackHandler {
        override fun startup(): Long = 0
        override fun shutdown(): Long = 0
        override fun onEmitStatus(l: Long, s: String?): Long = 0
    }

    companion object {
        /** A server must deliver this much within [PROBE_TIMEOUT_SEC] to be used for the test. */
        private const val PROBE_BYTES = 512L * 1024
        private const val PROBE_TIMEOUT_SEC = 8L

        private val UPLOAD_CHUNK = Random.nextBytes(128 * 1024)

        /**
         * (steady-state Mbps, best one-second Mbps) from a byte-count curve. Samples before
         * [warmup] seconds are dropped unless that leaves less than 0.8 s of data.
         */
        internal fun slope(samples: List<Pair<Double, Long>>, warmup: Double): Pair<Double?, Double?> {
            if (samples.size < 2) return null to null
            var start = samples.indexOfFirst { it.first >= warmup }.takeIf { it >= 0 } ?: 0
            if (samples.last().first - samples[start].first < 0.8) start = 0
            val a = samples[start]
            val b = samples.last()
            val span = b.first - a.first
            val avg = if (span > 0) (b.second - a.second) * 8 / span / 1e6 else null
            var peak: Double? = null
            val window = 10
            for (i in start until samples.size - window) {
                val lo = samples[i]
                val hi = samples[i + window]
                val dt = hi.first - lo.first
                if (dt <= 0) continue
                val mbps = (hi.second - lo.second) * 8 / dt / 1e6
                peak = max(peak ?: mbps, mbps)
            }
            return avg to peak
        }

        @JvmName("slopeSamples")
        private fun slope(samples: List<Sample>, warmup: Double): Pair<Double?, Double?> =
            slope(samples.map { it.t to it.n }, warmup)
    }
}
