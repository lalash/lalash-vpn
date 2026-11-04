package com.v2ray.ang.dto.entities

/**
 * Per-config test results. Fields after [testDelayMillis] come from the speed test; they are
 * nullable so records saved by older versions (ping only) still parse.
 */
data class ServerAffiliationInfo(
    var testDelayMillis: Long = 0L,
    var jitterMillis: Long? = null,
    var downloadMbps: Double? = null,
    var uploadMbps: Double? = null,
)
