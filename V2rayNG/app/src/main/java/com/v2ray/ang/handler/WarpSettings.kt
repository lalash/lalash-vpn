package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.Utils

/**
 * Connection modes served by the bundled Aether engine. Each mode is an entry in the config
 * list: a SOCKS profile pointing at the local port where that mode's exit is served.
 */
enum class EngineMode(val key: String, val remarks: String, val socksPort: Int, val usesWarp: Boolean) {
    WARP("warp", "WARP", AppConfig.WARP_SOCKS_PORT, true),
    WARP_TOR("warp_tor", "WARP → Tor", AppConfig.ENGINE_TOR_PORT, true),
    WARP_PSIPHON("warp_psiphon", "WARP → Psiphon", AppConfig.ENGINE_PSIPHON_PORT, true),
    TOR("tor", "Tor", AppConfig.WARP_SOCKS_PORT, false),
    PSIPHON("psiphon", "Psiphon", AppConfig.WARP_SOCKS_PORT, false);

    val usesPsiphon: Boolean get() = this == WARP_PSIPHON || this == PSIPHON
    val usesTor: Boolean get() = this == WARP_TOR || this == TOR

    companion object {
        fun fromKey(key: String?): EngineMode? = entries.firstOrNull { it.key == key }
    }
}

/** What the exit chain is set to: [exit] is a `CHAIN_*` value, [region] the Psiphon country. */
data class ExitChain(val exit: String = WarpSettings.CHAIN_OFF, val region: String = "") {
    val isOn: Boolean get() = exit != WarpSettings.CHAIN_OFF
}

/**
 * Engine options. Option values are the engine's own names, stored as strings.
 */
data class WarpSettings(
    val protocol: String = PROTOCOL_AUTO,
    val scan: String = SCAN_TURBO,
    val ipVersion: String = IP_V4,
    val noize: String = NOIZE_OFF,
    /** Psiphon exit country (ISO code), empty = automatic. */
    val psiphonRegion: String = "",
    /** Exit chain behind ordinary configs: [CHAIN_OFF], [CHAIN_WARP] or [CHAIN_PSIPHON]. */
    val chainExit: String = CHAIN_OFF,
) {
    /**
     * Protocols to try, in order. "Auto" starts with the one that last worked, then WireGuard
     * (UDP MASQUE is often unreachable on Iranian networks while WireGuard connects in seconds).
     * Through one of the user's configs only MASQUE over HTTP/2 is used: a proxy config may not
     * carry UDP, which WireGuard and HTTP/3 need.
     */
    fun protocolCandidates(lastWorking: String?, throughConfig: Boolean): List<String> = when {
        throughConfig -> listOf(PROTOCOL_MASQUE)
        protocol != PROTOCOL_AUTO -> listOf(protocol)
        else -> (listOfNotNull(lastWorking?.takeIf { it in AUTO_ORDER }) + AUTO_ORDER).distinct()
    }

    /** Engine arguments for [mode] using WARP protocol [proto], optionally through [upstream]. */
    fun toArgs(mode: EngineMode, proto: String, upstream: String?): List<String> = buildList {
        add("--bind"); add("${AppConfig.LOOPBACK}:${AppConfig.WARP_SOCKS_PORT}")
        when (mode) {
            EngineMode.TOR -> add("--tor-only")
            EngineMode.PSIPHON -> {
                add("--psiphon-only")
                addRegion()
            }
            EngineMode.WARP_TOR -> {
                add("--tor"); add("--tor-bind"); add("${AppConfig.LOOPBACK}:${AppConfig.ENGINE_TOR_PORT}")
            }
            EngineMode.WARP_PSIPHON -> {
                add("--psiphon"); add("--psiphon-bind"); add("${AppConfig.LOOPBACK}:${AppConfig.ENGINE_PSIPHON_PORT}")
                addRegion()
            }
            EngineMode.WARP -> Unit
        }
        if (mode.usesWarp) addWarp(proto, http2 = upstream != null)
        upstream?.let { add("--upstream"); add(it) }
    }

    private fun MutableList<String>.addRegion() {
        if (psiphonRegion.isNotEmpty()) {
            add("--psiphon-region"); add(psiphonRegion)
        }
    }

    private fun MutableList<String>.addWarp(proto: String, http2: Boolean) {
        add(
            when (proto) {
                PROTOCOL_WIREGUARD -> "--wg"
                PROTOCOL_GOOL -> "--gool"
                PROTOCOL_MIM -> "--mim"
                else -> "--masque"
            }
        )
        if (http2 && proto == PROTOCOL_MASQUE) add("--h2")
        add(
            when (scan) {
                SCAN_TURBO -> "--turbo"
                SCAN_THOROUGH -> "--thorough"
                SCAN_STEALTH -> "--stealth"
                SCAN_IRONCLAD -> "--ironclad"
                else -> "--balanced"
            }
        )
        add(
            when (ipVersion) {
                IP_V6 -> "-6"
                IP_BOTH -> "--dual"
                else -> "-4"
            }
        )
        add("--quick-reconnect")
        if (noize in NOIZE_PROFILES && noize != NOIZE_OFF) {
            add("--noize"); add(noize)
        }
    }

    companion object {
        const val PROTOCOL_AUTO = "auto"
        const val PROTOCOL_MASQUE = "masque"
        const val PROTOCOL_WIREGUARD = "wireguard"
        const val PROTOCOL_GOOL = "gool"
        const val PROTOCOL_MIM = "mim"
        val PROTOCOLS = listOf(PROTOCOL_AUTO, PROTOCOL_WIREGUARD, PROTOCOL_MASQUE, PROTOCOL_GOOL, PROTOCOL_MIM)
        val AUTO_ORDER = listOf(PROTOCOL_WIREGUARD, PROTOCOL_GOOL, PROTOCOL_MASQUE, PROTOCOL_MIM)

        const val SCAN_TURBO = "turbo"
        const val SCAN_BALANCED = "balanced"
        const val SCAN_THOROUGH = "thorough"
        const val SCAN_STEALTH = "stealth"
        const val SCAN_IRONCLAD = "ironclad"
        val SCANS = listOf(SCAN_TURBO, SCAN_BALANCED, SCAN_THOROUGH, SCAN_STEALTH, SCAN_IRONCLAD)

        const val IP_V4 = "v4"
        const val IP_V6 = "v6"
        const val IP_BOTH = "both"
        val IP_VERSIONS = listOf(IP_V4, IP_V6, IP_BOTH)

        const val NOIZE_OFF = "off"
        val NOIZE_PROFILES = listOf(NOIZE_OFF, "light", "firewall", "balanced", "gfw", "aggressive")

        /** Psiphon exit countries offered in settings; "" = automatic. */
        val PSIPHON_REGIONS = listOf(
            "",
            "AE", "AR", "AT", "AU", "BE", "BG", "BR", "CA", "CH", "CL", "CO", "CY",
            "CZ", "DE", "DK", "EE", "ES", "FI", "FR", "GB", "GR", "HK", "HR", "HU",
            "IE", "IL", "IN", "IS", "IT", "JP", "KR", "LT", "LU", "LV", "MD", "MX",
            "MY", "NL", "NO", "NZ", "PH", "PL", "PT", "RO", "RS", "SE", "SG", "SK",
            "TH", "TR", "TW", "UA", "US", "VN", "ZA",
        )

        private const val KEY_MODE_PREFIX = "engine_mode_"

        const val CHAIN_OFF = ""
        const val CHAIN_WARP = "warp"
        const val CHAIN_PSIPHON = "psiphon"
        val CHAIN_EXITS = listOf(CHAIN_OFF, CHAIN_WARP, CHAIN_PSIPHON)

        fun exitChain(): ExitChain = load().let { ExitChain(it.chainExit, it.psiphonRegion) }

        /**
         * The engine mode that carries traffic after the config [guid], or null when the exit
         * chain is off or [guid] is itself an engine entry (WARP, Tor, Psiphon need no chain).
         */
        fun chainModeFor(guid: String?): EngineMode? {
            if (guid.isNullOrEmpty() || modeFor(guid) != null) return null
            return when (load().chainExit) {
                CHAIN_WARP -> EngineMode.WARP
                CHAIN_PSIPHON -> EngineMode.PSIPHON
                else -> null
            }
        }

        fun load(): WarpSettings {
            val d = WarpSettings()
            fun pick(key: String, allowed: List<String>, default: String) =
                MmkvManager.decodeSettingsString(key)?.takeIf { it in allowed } ?: default
            return WarpSettings(
                protocol = pick(AppConfig.PREF_WARP_PROTOCOL, PROTOCOLS, d.protocol),
                scan = pick(AppConfig.PREF_WARP_SCAN, SCANS, d.scan),
                ipVersion = pick(AppConfig.PREF_WARP_IP, IP_VERSIONS, d.ipVersion),
                noize = pick(AppConfig.PREF_WARP_NOIZE, NOIZE_PROFILES, d.noize),
                psiphonRegion = pick(AppConfig.PREF_ENGINE_PSIPHON_REGION, PSIPHON_REGIONS, d.psiphonRegion),
                chainExit = pick(AppConfig.PREF_ENGINE_CHAIN_EXIT, CHAIN_EXITS, d.chainExit),
            )
        }

        /** The engine mode [guid] stands for, or null for an ordinary config. */
        fun modeFor(guid: String?): EngineMode? {
            if (guid.isNullOrEmpty()) return null
            EngineMode.fromKey(MmkvManager.decodeSettingsString(KEY_MODE_PREFIX + guid))?.let { return it }
            // Entry created by the first WARP version, before modes existed.
            return if (guid == MmkvManager.decodeSettingsString(AppConfig.PREF_WARP_GUID)) EngineMode.WARP else null
        }

        fun isWarpProfile(guid: String?): Boolean = modeFor(guid) != null

        /**
         * Creates the engine entries that are missing (one per [EngineMode]) in their own group,
         * selects the entry for [select] and returns its GUID.
         */
        fun ensureEngineEntries(select: EngineMode = EngineMode.WARP): String {
            val subs = MmkvManager.decodeSubscriptions()
            val existing = subs.firstOrNull {
                it.subscription.url.isEmpty() &&
                    (it.subscription.remarks == AppConfig.ENGINE_SUBSCRIPTION_REMARKS || it.subscription.remarks == AppConfig.WARP_SUBSCRIPTION_REMARKS)
            }
            val subId = if (existing != null) {
                if (existing.subscription.remarks != AppConfig.ENGINE_SUBSCRIPTION_REMARKS) {
                    existing.subscription.remarks = AppConfig.ENGINE_SUBSCRIPTION_REMARKS
                    MmkvManager.encodeSubscription(existing.guid, existing.subscription)
                }
                existing.guid
            } else {
                Utils.getUuid().also {
                    MmkvManager.encodeSubscription(
                        it,
                        SubscriptionItem(remarks = AppConfig.ENGINE_SUBSCRIPTION_REMARKS, autoUpdate = false),
                    )
                    // The engine only runs while connected, so pinging these entries is pointless.
                    MmkvManager.encodeSubAutoPingMinutes(it, 0)
                }
            }
            val present = MmkvManager.decodeServerList(subId).associateBy { modeFor(it) }
            var selected = ""
            // Insert in reverse so the list reads WARP, WARP → Tor, ... (new entries go on top).
            EngineMode.entries.reversed().forEach { mode ->
                val guid = present[mode] ?: run {
                    val profile = ProfileItem.create(EConfigType.SOCKS).apply {
                        remarks = mode.remarks
                        server = AppConfig.LOOPBACK
                        serverPort = mode.socksPort.toString()
                        subscriptionId = subId
                    }
                    MmkvManager.encodeServerConfig("", profile).also {
                        MmkvManager.encodeSettings(KEY_MODE_PREFIX + it, mode.key)
                    }
                }
                if (mode == EngineMode.WARP) MmkvManager.encodeSettings(AppConfig.PREF_WARP_GUID, guid)
                if (mode == select) selected = guid
            }
            MmkvManager.setSelectServer(selected)
            return selected
        }

        /** Kept for the "Add WARP" menu item. */
        fun ensureWarpProfile(): String = ensureEngineEntries(EngineMode.WARP)
    }
}
