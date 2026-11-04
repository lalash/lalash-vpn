package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.handler.AngConfigManager

internal data class ServerRowUiModel(
    val guid: String,
    val profile: ProfileItem,
    val remarks: String,
    val statistics: String,
    val typeDescription: String,
    val testDelayMillis: Long,
    val subscriptionBadge: String,
    val jitterMillis: Long? = null,
    val downloadMbps: Double? = null,
    val uploadMbps: Double? = null,
)

/** Display order of the config list. Stored by ordinal, so append new values at the end. */
enum class MainSort {
    SUBSCRIPTION, PING, JITTER, DOWNLOAD, UPLOAD, NAME;

    fun next(): MainSort = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromOrdinal(value: Int): MainSort = entries.getOrElse(value) { SUBSCRIPTION }
    }
}

/**
 * Orders rows for display. Untested or failed configs always go after tested ones;
 * [MainSort.SUBSCRIPTION] keeps the stored order (the only order that allows dragging).
 */
internal fun sortServerRows(rows: List<ServerRowUiModel>, sort: MainSort): List<ServerRowUiModel> = when (sort) {
    MainSort.SUBSCRIPTION -> rows
    MainSort.PING -> rows.sortedBy { if (it.testDelayMillis > 0) it.testDelayMillis else Long.MAX_VALUE }
    MainSort.JITTER -> rows.sortedBy { it.jitterMillis ?: Long.MAX_VALUE }
    MainSort.DOWNLOAD -> rows.sortedByDescending { it.downloadMbps ?: -1.0 }
    MainSort.UPLOAD -> rows.sortedByDescending { it.uploadMbps ?: -1.0 }
    MainSort.NAME -> rows.sortedBy { it.remarks.lowercase() }
}

internal data class ServerGroupUiState(
    val servers: List<ServersCache> = emptyList(),
    val rows: List<ServerRowUiModel> = emptyList(),
)

internal fun buildServerRowUiModel(
    server: ServersCache,
    subscriptionRemarks: String,
): ServerRowUiModel {
    val profile = server.profile
    return ServerRowUiModel(
        guid = server.guid,
        profile = profile,
        remarks = profile.remarks,
        statistics = profile.description.nullIfBlank()
            ?: AngConfigManager.generateDescription(profile),
        typeDescription = serverProtocolDescription(profile),
        testDelayMillis = server.testDelayMillis,
        subscriptionBadge = subscriptionRemarks.firstOrNull()?.toString().orEmpty(),
        jitterMillis = server.jitterMillis,
        downloadMbps = server.downloadMbps,
        uploadMbps = server.uploadMbps,
    )
}

private fun serverProtocolDescription(profile: ProfileItem): String {
    if (profile.configType.isComplexType()) return profile.configType.name
    val parts = mutableListOf(profile.configType.name)
    profile.network?.let { network ->
        if (network.isNotBlank() && !network.equals("tcp", ignoreCase = true)) {
            parts.add(network)
        }
    }
    profile.security?.let { security ->
        if (security.isNotBlank()) {
            parts.add(
                if (profile.insecure == true && security.equals("tls", ignoreCase = true)) {
                    "$security insecure"
                } else {
                    security
                }
            )
        }
    }
    return parts.joinToString(" / ")
}
