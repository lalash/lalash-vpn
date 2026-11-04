package com.v2ray.ang.dto

import java.io.Serializable

/** What a config speed test measures. Stored by ordinal in settings, so keep the order. */
enum class SpeedTestMode {
    UPLOAD_DOWNLOAD,
    UPLOAD_ONLY,
    DOWNLOAD_ONLY,
    PING_ONLY;

    val measuresUpload: Boolean get() = this == UPLOAD_DOWNLOAD || this == UPLOAD_ONLY
    val measuresDownload: Boolean get() = this == UPLOAD_DOWNLOAD || this == DOWNLOAD_ONLY

    companion object {
        fun fromOrdinal(value: Int): SpeedTestMode = entries.getOrElse(value) { UPLOAD_DOWNLOAD }
    }
}

/** Where a config is in the two-phase run. */
enum class SpeedTestState { QUEUED, PINGING, TESTING, DONE, DEAD, SKIPPED }

/** Start command sent from the UI to SpeedTestService. */
data class SpeedTestRequest(
    val guids: List<String>,
    val mode: SpeedTestMode,
    val topN: Int,
) : Serializable

/** Result for one config, sent from SpeedTestService each time it changes. */
data class SpeedTestResult(
    val guid: String,
    val state: SpeedTestState,
    val pingMs: Long? = null,
    val jitterMs: Long? = null,
    val uploadMbps: Double? = null,
    val downloadMbps: Double? = null,
    val liveMbps: Double? = null,
    val exitIp: String? = null,
    val exitCountry: String? = null,
    val error: String? = null,
) : Serializable

/** One progress update from SpeedTestService: overall counters plus the result that changed. */
data class SpeedTestUpdate(
    val pingDone: Int,
    val pingTotal: Int,
    val speedDone: Int,
    val speedTotal: Int,
    val currentGuid: String?,
    val result: SpeedTestResult?,
) : Serializable
