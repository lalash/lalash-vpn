package com.v2ray.ang.dto.entities

data class SubscriptionItem(
    var remarks: String = "",
    var url: String = "",
    var enabled: Boolean = true,
    val addedTime: Long = System.currentTimeMillis(),
    var lastUpdated: Long = -1,
    // New subscriptions update themselves every 12 hours by default. Saved records always carry
    // these fields, so changing the defaults does not alter existing subscriptions.
    var autoUpdate: Boolean = true,
    var updateInterval: Long = 720, // in minutes
    var prevProfile: String? = null,
    var nextProfile: String? = null,
    var filter: String? = null,
    var allowInsecureUrl: Boolean = false,
    var userAgent: String? = null,
    var requestHeaders: String? = null,
    var usageInfo: SubscriptionUsageInfo? = null,
)

