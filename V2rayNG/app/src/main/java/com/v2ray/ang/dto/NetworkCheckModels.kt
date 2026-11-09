package com.v2ray.ang.dto

/** Sections of the network check, in display order. */
enum class CheckGroup { NETWORK, IRAN_SITES, IRAN_DNS, FOREIGN_SITES, FOREIGN_DNS }

enum class CheckStatus { PENDING, OK, WARN, FAIL, SKIPPED }

/** Why a check failed; drives the per-row reason text and the conclusion. */
enum class FailKind {
    NO_PING, DNS_FAILED, DNS_POISONED, CONNECT_TIMEOUT, CONNECT_REFUSED, TLS_BLOCKED, NO_DNS_ANSWER, HTTP_ERROR
}

/**
 * One row of the network check. [label] is a site or server name, [detail] its address; [pingMs]
 * is the ICMP round trip (or TCP connect time when [pingByTcp]), [timeMs] the full HTTPS or DNS
 * answer time.
 */
data class CheckItem(
    val id: String,
    val group: CheckGroup,
    val label: String,
    val detail: String = "",
    val status: CheckStatus = CheckStatus.PENDING,
    val pingMs: Long? = null,
    val pingByTcp: Boolean = false,
    val timeMs: Long? = null,
    val fail: FailKind? = null,
)

enum class NetworkVerdict {
    NO_NETWORK, ROUTER_PROBLEM, NO_INTERNET, PHONE_DNS_BROKEN, INTERNATIONAL_CUT, FILTERED_NORMAL, ALL_GOOD
}

/** Overall result plus the extra findings shown under it. */
data class NetworkConclusion(
    val verdict: NetworkVerdict,
    val dnsPoisoned: Boolean = false,
    val tlsFiltering: Boolean = false,
    val udpAbroadBlocked: Boolean = false,
    val packetsWithoutReply: Boolean = false,
)

/** What the network looked like when the check ran. */
data class NetworkInfo(
    val connected: Boolean,
    val transport: Transport,
    val validated: Boolean,
    val gateway: String?,
    /** A VPN (this app's or another) is up; the checks still use the phone's own connection. */
    val vpnActive: Boolean,
) {
    enum class Transport { WIFI, CELLULAR, ETHERNET, OTHER, NONE }
}
