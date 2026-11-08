package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig

/**
 * Tunables for the config speed test, read from settings. Numbers are stored as strings
 * because the settings screen edits them through text fields.
 */
data class SpeedTestSettings(
    val durationDownloadSec: Double = 10.0,
    val durationUploadSec: Double = 12.0,
    val warmupDownloadSec: Double = 2.0,
    val warmupUploadSec: Double = 3.0,
    val streamsDownload: Int = 4,
    val streamsUpload: Int = 4,
    val pingProbes: Int = 11,
    val pingConcurrency: Int = 8,
    val maxPingMs: Long = 3000,
    val requestTimeoutSec: Double = 15.0,
    val skipDead: Boolean = true,
    val fetchExitIp: Boolean = false,
    val verifyTls: Boolean = true,
    /** Download URLs in the order they are tried; the next one is used when one fails. */
    val downUrls: List<String> = DOWNLOAD_URLS,
    val upUrl: String = AppConfig.ST_DEFAULT_UP_URL,
    val pingUrl: String = AppConfig.ST_DEFAULT_PING_URL,
    val downloadSizeMb: Int = 64,
) {
    companion object {
        const val DEFAULT_TOP_N = 10

        /**
         * Download test URLs offered in the dropdown, same list and order as the desktop tester;
         * `{bytes}` is replaced with the size to fetch. The first is the default.
         */
        val DOWNLOAD_URLS = listOf(
            "https://cachefly.cachefly.net/100mb.test",
            "https://speed.cloudflare.com/__down?bytes={bytes}",
            "https://proof.ovh.net/files/100Mb.dat",
            "https://fsn1-speed.hetzner.com/100MB.bin",
        )

        /**
         * Download URLs to try, in order: the selected one first, then the rest of the list as
         * fallbacks for when it sends no data through a config. A stored URL that is not in the
         * list (an older custom value) is kept first.
         */
        fun resolveDownUrls(selected: String?): List<String> {
            val first = selected?.trim()?.takeIf { it.isNotEmpty() }
            return (listOfNotNull(first) + DOWNLOAD_URLS).distinct()
        }

        /** Stored instead of a URL to follow the app's own real-ping test URL (Settings). */
        const val PING_URL_SAME_AS_APP = "app"

        /** Ping/jitter targets offered in speed test settings, as (label, value). */
        val PING_URL_PRESETS = listOf(
            "Cloudflare" to AppConfig.ST_DEFAULT_PING_URL,
            "Google (gstatic)" to AppConfig.DELAY_TEST_URL,
            "Google" to AppConfig.DELAY_TEST_URL2,
            "Apple" to "http://captive.apple.com/hotspot-detect.html",
            "Microsoft" to "http://www.msftconnecttest.com/connecttest.txt",
            "Firefox" to "http://detectportal.firefox.com/success.txt",
        )

        fun resolvePingUrl(value: String): String =
            if (value == PING_URL_SAME_AS_APP) SettingsManager.getDelayTestUrl() else value

        fun load(): SpeedTestSettings {
            val d = SpeedTestSettings()
            return SpeedTestSettings(
                durationDownloadSec = double(AppConfig.PREF_ST_DURATION_DOWNLOAD, d.durationDownloadSec, 2.0, 120.0),
                durationUploadSec = double(AppConfig.PREF_ST_DURATION_UPLOAD, d.durationUploadSec, 2.0, 120.0),
                warmupDownloadSec = double(AppConfig.PREF_ST_WARMUP_DOWNLOAD, d.warmupDownloadSec, 0.0, 30.0),
                warmupUploadSec = double(AppConfig.PREF_ST_WARMUP_UPLOAD, d.warmupUploadSec, 0.0, 30.0),
                streamsDownload = int(AppConfig.PREF_ST_STREAMS_DOWNLOAD, d.streamsDownload, 1, 16),
                streamsUpload = int(AppConfig.PREF_ST_STREAMS_UPLOAD, d.streamsUpload, 1, 16),
                pingProbes = int(AppConfig.PREF_ST_PING_PROBES, d.pingProbes, 1, 50),
                pingConcurrency = int(AppConfig.PREF_ST_PING_CONCURRENCY, d.pingConcurrency, 1, 64),
                maxPingMs = int(AppConfig.PREF_ST_MAX_PING, d.maxPingMs.toInt(), 0, 60_000).toLong(),
                requestTimeoutSec = double(AppConfig.PREF_ST_REQUEST_TIMEOUT, d.requestTimeoutSec, 2.0, 120.0),
                skipDead = MmkvManager.decodeSettingsBool(AppConfig.PREF_ST_SKIP_DEAD, d.skipDead),
                fetchExitIp = MmkvManager.decodeSettingsBool(AppConfig.PREF_ST_FETCH_EXIT_IP, d.fetchExitIp),
                verifyTls = MmkvManager.decodeSettingsBool(AppConfig.PREF_ST_VERIFY_TLS, d.verifyTls),
                downUrls = resolveDownUrls(MmkvManager.decodeSettingsString(AppConfig.PREF_ST_DOWN_URL)),
                upUrl = string(AppConfig.PREF_ST_UP_URL, d.upUrl),
                pingUrl = resolvePingUrl(string(AppConfig.PREF_ST_PING_URL, d.pingUrl)),
                downloadSizeMb = int(AppConfig.PREF_ST_DOWNLOAD_SIZE_MB, d.downloadSizeMb, 1, 90),
            )
        }

        /** Restores every speed test setting to its default by removing the stored values. */
        fun resetToDefaults() {
            listOf(
                AppConfig.PREF_ST_DURATION_DOWNLOAD, AppConfig.PREF_ST_DURATION_UPLOAD,
                AppConfig.PREF_ST_WARMUP_DOWNLOAD, AppConfig.PREF_ST_WARMUP_UPLOAD,
                AppConfig.PREF_ST_STREAMS_DOWNLOAD, AppConfig.PREF_ST_STREAMS_UPLOAD,
                AppConfig.PREF_ST_PING_PROBES, AppConfig.PREF_ST_PING_CONCURRENCY,
                AppConfig.PREF_ST_MAX_PING, AppConfig.PREF_ST_REQUEST_TIMEOUT,
                AppConfig.PREF_ST_DOWN_URL, AppConfig.PREF_ST_UP_URL, AppConfig.PREF_ST_PING_URL,
                AppConfig.PREF_ST_DOWNLOAD_SIZE_MB,
            ).forEach { MmkvManager.encodeSettings(it, null as String?) }
            val d = SpeedTestSettings()
            MmkvManager.encodeSettings(AppConfig.PREF_ST_SKIP_DEAD, d.skipDead)
            MmkvManager.encodeSettings(AppConfig.PREF_ST_FETCH_EXIT_IP, d.fetchExitIp)
            MmkvManager.encodeSettings(AppConfig.PREF_ST_VERIFY_TLS, d.verifyTls)
        }

        private fun string(key: String, default: String): String =
            MmkvManager.decodeSettingsString(key)?.trim()?.takeIf { it.isNotEmpty() } ?: default

        private fun double(key: String, default: Double, min: Double, max: Double): Double =
            (MmkvManager.decodeSettingsString(key)?.trim()?.toDoubleOrNull() ?: default).coerceIn(min, max)

        private fun int(key: String, default: Int, min: Int, max: Int): Int =
            (MmkvManager.decodeSettingsString(key)?.trim()?.toIntOrNull() ?: default).coerceIn(min, max)
    }
}
