package com.v2ray.ang.dto

import com.v2ray.ang.dto.entities.SubscriptionUsageInfo

data class GroupMapItem(
    var id: String,
    var remarks: String,
    val usageInfo: SubscriptionUsageInfo? = null,
    /** Subscription link; empty for local groups such as the default group. */
    val url: String = "",
    val autoUpdate: Boolean = false,
    val updateIntervalMinutes: Long = 1440,
    /** Auto ping interval in minutes, 0 = off. */
    val autoPingMinutes: Int = 0,
    /** Added recently and not looked at yet: shown first with a "new" mark. */
    val isNew: Boolean = false,
)