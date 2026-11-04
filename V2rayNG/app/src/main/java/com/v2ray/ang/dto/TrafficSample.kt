package com.v2ray.ang.dto

import java.io.Serializable

/**
 * One live traffic reading, sent from the daemon to the UI about once per second.
 * Rates are in bytes per second; session totals count proxied bytes since the core started.
 */
data class TrafficSample(
    val proxyUpBps: Double,
    val proxyDownBps: Double,
    val sessionUpBytes: Long,
    val sessionDownBytes: Long,
    val timestampMillis: Long,
    val sessionStartMillis: Long,
) : Serializable
