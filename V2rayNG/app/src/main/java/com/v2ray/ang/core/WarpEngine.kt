package com.v2ray.ang.core

import android.content.Context
import android.util.Base64
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.EngineMode
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.WarpSettings
import com.v2ray.ang.util.LogUtil
import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Runs the Aether engine (CluvexStudio/Aether, AGPL-3.0) as a child process of the daemon, for
 * the connection modes in [EngineMode]: WARP, Tor, Psiphon and their chains.
 *
 * The engine registers a free Cloudflare WARP identity on first use and keeps it in its working
 * directory. Each mode's exit is served as SOCKS5 on a local port; the matching entry in the
 * config list is a plain SOCKS profile pointing at that port, so traffic still goes through Xray
 * and keeps the app's routing (Iran route, per-app, stats).
 *
 * With the exit chain on, the user's selected config runs as usual and the engine dials out
 * through it (`--upstream`, a SOCKS inbound of the main core), so the path becomes:
 * your config → WARP / Psiphon → internet.
 *
 * With the "Auto" WARP protocol a supervisor tries protocols in turn: if the engine does not
 * report a validated tunnel within [ATTEMPT_TIMEOUT_SECONDS] the next one is tried, and the one
 * that worked is remembered. Once up, the engine handles its own reconnects.
 *
 * Android only allows executing files packaged in jniLibs, so the engine, Psiphon and the Tor
 * bridge transport ship as `lib*.so` and run from nativeLibraryDir. The app's own traffic is
 * excluded from the VPN, so their sockets reach the network directly.
 */
object WarpEngine {
    private const val BINARY = "libaether.so"
    private const val PSIPHON_BINARY = "libpsiphon.so"
    private const val LYREBIRD_BINARY = "libpt-lyrebird.so"
    private const val WORK_DIR = "warp"
    private const val PSIPHON_SERVER_ASSET = "psiphon_server_entries.txt"
    private const val READY_MARKER = "serving socks5"
    private const val ATTEMPT_TIMEOUT_SECONDS = 40L
    private const val PSIPHON_DATASTORE = "psiphon/ca.psiphon.PsiphonTunnel.tunnel-core/datastore"
    private const val PSIPHON_OVERLAY = "psiphon-config.json"

    /** Public resolvers Psiphon uses when the system resolver is unreachable from a child process. */
    private val PSIPHON_DNS = listOf("1.1.1.1", "8.8.8.8", "9.9.9.9")

    @Volatile
    private var process: Process? = null

    @Volatile
    private var supervisor: Thread? = null

    @Volatile
    private var stopped = true

    @Volatile
    private var runningMode: EngineMode? = null

    @Volatile
    private var runningUpstream: String? = null

    @Volatile
    private var datastoreLocked = false

    val isRunning: Boolean get() = !stopped && supervisor?.isAlive == true

    /**
     * Starts the engine for [mode] unless it already runs it the same way. With [upstream]
     * (`socks5://host:port`) the engine dials everything through that proxy: the exit chain
     * behind the user's selected config. Safe on every core (re)launch.
     */
    @Synchronized
    fun start(context: Context, mode: EngineMode, upstream: String? = null) {
        if (isRunning && runningMode == mode && runningUpstream == upstream) return
        stop()
        reapOrphans()
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val bin = File(nativeDir, BINARY)
        if (!bin.exists()) error("WARP engine is not included in this build")
        val workDir = File(context.filesDir, WORK_DIR).apply { mkdirs() }
        val settings = WarpSettings.load()

        val env = environment(context, workDir, nativeDir, mode)
        val candidates = if (mode.usesWarp) {
            settings.protocolCandidates(MmkvManager.decodeSettingsString(AppConfig.PREF_WARP_LAST_PROTOCOL), upstream != null)
        } else {
            listOf("")
        }
        stopped = false
        runningMode = mode
        runningUpstream = upstream
        supervisor = Thread({ supervise(bin, workDir, env, settings, mode, candidates, upstream) }, "warp-engine").apply {
            isDaemon = true
            start()
        }
    }

    /** Stops the engine and waits briefly for it to exit. */
    @Synchronized
    fun stop() {
        stopped = true
        runningMode = null
        runningUpstream = null
        supervisor?.interrupt()
        supervisor = null
        killProcess()
    }

    /**
     * Kills engine and Psiphon processes left by an earlier daemon (crash, force stop). Psiphon
     * locks its datastore, so a leftover copy makes every new start fail with
     * "tryDatastoreOpenDB timeout". Only this app's own processes are visible and killable.
     */
    private fun reapOrphans() {
        val self = android.os.Process.myPid()
        File("/proc").listFiles()?.forEach { dir ->
            val pid = dir.name.toIntOrNull() ?: return@forEach
            if (pid == self) return@forEach
            val cmd = try {
                File(dir, "cmdline").readText()
            } catch (_: Exception) {
                return@forEach
            }
            if (cmd.contains(BINARY) || cmd.contains(PSIPHON_BINARY)) {
                LogUtil.w(AppConfig.TAG, "WarpEngine: killing leftover engine process $pid")
                android.os.Process.killProcess(pid)
            }
        }
    }

    private fun supervise(
        bin: File,
        workDir: File,
        env: Map<String, String>,
        settings: WarpSettings,
        mode: EngineMode,
        candidates: List<String>,
        upstream: String?,
    ) {
        var index = 0
        while (!stopped) {
            val proto = candidates[index % candidates.size]
            val ready = CountDownLatch(1)
            val proc = try {
                launch(bin, workDir, env, settings.toArgs(mode, proto, upstream), ready)
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "WarpEngine: failed to launch (${mode.key} $proto)", e)
                return
            }
            process = proc
            // Tor and Psiphon alone have no WARP protocol to switch: let the engine keep trying.
            val up = if (!mode.usesWarp || candidates.size == 1) {
                true
            } else {
                try {
                    ready.await(ATTEMPT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                } catch (_: InterruptedException) {
                    false
                }
            }
            if (stopped) return
            if (up) {
                if (mode.usesWarp && proto.isNotEmpty()) {
                    Thread {
                        try {
                            if (ready.await(5, TimeUnit.MINUTES) && !stopped) {
                                LogUtil.w(AppConfig.TAG, "WarpEngine: tunnel up with $proto")
                                MmkvManager.encodeSettings(AppConfig.PREF_WARP_LAST_PROTOCOL, proto)
                            }
                        } catch (_: InterruptedException) {
                        }
                    }.apply { isDaemon = true }.start()
                }
                // The engine reconnects by itself from here on; wait until it exits or we stop.
                try {
                    proc.waitFor()
                } catch (_: InterruptedException) {
                    return
                }
                if (stopped) return
                LogUtil.w(AppConfig.TAG, "WarpEngine: engine exited (${proc.exitValue()}), restarting")
                if (datastoreLocked) {
                    datastoreLocked = false
                    reapOrphans()
                    File(workDir, PSIPHON_DATASTORE).deleteRecursively()
                    LogUtil.w(AppConfig.TAG, "WarpEngine: Psiphon datastore was locked, cleared it")
                }
                try {
                    Thread.sleep(2000)
                } catch (_: InterruptedException) {
                    return
                }
                continue
            }
            LogUtil.w(AppConfig.TAG, "WarpEngine: no tunnel with $proto in ${ATTEMPT_TIMEOUT_SECONDS}s, trying the next protocol")
            killProcess()
            index++
        }
    }

    private fun launch(bin: File, workDir: File, env: Map<String, String>, args: List<String>, ready: CountDownLatch): Process {
        val command = listOf(bin.absolutePath) + args
        LogUtil.w(AppConfig.TAG, "WarpEngine: starting ${args.joinToString(" ")}")
        val builder = ProcessBuilder(command)
            .directory(workDir)
            .redirectErrorStream(true)
        builder.environment().putAll(env)
        val proc = builder.start()
        // Drain the output so the engine never blocks on a full pipe, and watch for readiness.
        Thread({
            try {
                proc.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (line.contains(READY_MARKER)) ready.countDown()
                        if (line.contains("tryDatastoreOpenDB")) datastoreLocked = true
                        if (line.contains("WARN") || line.contains("ERROR") || line.contains("[+]")) {
                            LogUtil.w(AppConfig.TAG, "WarpEngine: $line")
                        }
                    }
                }
            } catch (_: Exception) {
                // stream closed when the process ends
            }
        }, "warp-engine-log").apply { isDaemon = true }.start()
        return proc
    }

    private fun environment(context: Context, workDir: File, nativeDir: String, mode: EngineMode): Map<String, String> = buildMap {
        // The engine keeps its WARP identity under HOME; a lost identity means a new
        // Cloudflare registration on every start, which Cloudflare rate-limits.
        put("HOME", workDir.absolutePath)
        put("TMPDIR", workDir.absolutePath)
        put("NO_COLOR", "1")
        if (mode.usesTor) {
            // A persistent directory cache, or Tor bootstraps from nothing on every connect.
            put("AETHER_TOR_DIR", File(workDir, "tor").apply { mkdirs() }.absolutePath)
            put("AETHER_TOR_PT_DIR", nativeDir)
            val lyrebird = File(nativeDir, LYREBIRD_BINARY)
            if (lyrebird.exists()) {
                // One lyrebird binary serves all three bridge transports.
                put("AETHER_TOR_PT", listOf("obfs4", "meek_lite", "webtunnel").joinToString(";") { "$it=${lyrebird.absolutePath}" })
            }
        }
        if (mode.usesPsiphon) {
            val dir = File(workDir, "psiphon").apply { mkdirs() }
            put("AETHER_PSIPHON_DIR", dir.absolutePath)
            put("AETHER_PSIPHON_BIN", File(nativeDir, PSIPHON_BINARY).absolutePath)
            psiphonServerEntries(context, dir)?.let {
                put("AETHER_PSIPHON_SERVER_ENTRIES", it.absolutePath)
                put("AETHER_PSIPHON_EMBEDDED_SERVERS", it.absolutePath)
            }
            // Psiphon is a static Go program: on Android its certificate pool starts empty, so
            // hand it the system CAs as a PEM file or every TLS handshake fails.
            val ca = caBundle(dir)
            ca?.let { put("SSL_CERT_FILE", it.absolutePath) }
            put("SSL_CERT_DIR", "/apex/com.android.conscrypt/cacerts:/system/etc/security/cacerts")
            psiphonOverlay(dir, ca)?.let { put("AETHER_PSIPHON_CONFIG", it.absolutePath) }
        }
    }

    /** Psiphon's starting server list, copied from assets so it can connect before any download. */
    private fun psiphonServerEntries(context: Context, dir: File): File? = try {
        val target = File(dir, PSIPHON_SERVER_ASSET)
        if (!target.isFile || target.length() == 0L) {
            context.assets.open(PSIPHON_SERVER_ASSET).use { input -> target.outputStream().use { input.copyTo(it) } }
        }
        target
    } catch (e: Exception) {
        LogUtil.e(AppConfig.TAG, "WarpEngine: no Psiphon server list", e)
        null
    }

    /**
     * Settings merged into Psiphon's own config. Android has no /etc/resolv.conf, so without
     * explicit resolvers Psiphon's DNS fails with "connection refused".
     */
    private fun psiphonOverlay(dir: File, ca: File?): File? = try {
        val json = org.json.JSONObject().apply {
            put("DNSResolverAlternateServers", org.json.JSONArray(PSIPHON_DNS))
            ca?.let { put("TrustedCACertificatesFilename", it.absolutePath) }
        }
        File(dir, PSIPHON_OVERLAY).apply { writeText(json.toString()) }
    } catch (e: Exception) {
        LogUtil.e(AppConfig.TAG, "WarpEngine: could not write Psiphon settings", e)
        null
    }

    private fun caBundle(dir: File): File? = try {
        val target = File(dir, "ca-bundle.pem")
        if (!target.isFile || target.length() == 0L) {
            val store = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
            val pem = StringBuilder()
            for (alias in store.aliases()) {
                val cert = store.getCertificate(alias) as? X509Certificate ?: continue
                pem.append("-----BEGIN CERTIFICATE-----\n")
                pem.append(Base64.encodeToString(cert.encoded, Base64.DEFAULT))
                pem.append("-----END CERTIFICATE-----\n")
            }
            target.writeText(pem.toString())
        }
        target
    } catch (e: Exception) {
        LogUtil.e(AppConfig.TAG, "WarpEngine: could not export CA certificates", e)
        null
    }

    private fun killProcess() {
        val proc = process ?: return
        process = null
        try {
            proc.destroy()
            if (!proc.waitFor(3, TimeUnit.SECONDS)) proc.destroyForcibly()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "WarpEngine: failed to stop", e)
        }
    }
}
