package com.v2ray.ang.ui.dashboard

import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.ExitChain

/** Number of one-second points kept for the live traffic chart. */
internal const val DASHBOARD_HISTORY_SIZE = 60

data class DashboardUiState(
    val isRunning: Boolean = false,
    val configGuid: String? = null,
    val configName: String = "",
    val configType: EConfigType? = null,
    val ping: DashboardPing = DashboardPing.Idle,
    /** Ping tests still running; each tap starts one and every result is shown when it lands. */
    val pingsPending: Int = 0,
    val exitIp: String? = null,
    /** ISO 3166 alpha-2 code when the IP lookup returned one, otherwise whatever it returned. */
    val exitCountry: String? = null,
    val downloadBps: Double = 0.0,
    val uploadBps: Double = 0.0,
    val sessionDownloadBytes: Long = 0,
    val sessionUploadBytes: Long = 0,
    val sessionStartMillis: Long = 0,
    /** Newest last, in Mbps. */
    val downloadHistory: List<Float> = List(DASHBOARD_HISTORY_SIZE) { 0f },
    val uploadHistory: List<Float> = List(DASHBOARD_HISTORY_SIZE) { 0f },
    val iranDirect: Boolean = false,
    /** Exit chain behind the selected config, or null when off or not applicable. */
    val exitChain: ExitChain? = null,
)

sealed interface DashboardPing {
    data object Idle : DashboardPing
    data object Testing : DashboardPing
    data class Result(val millis: Long) : DashboardPing
    data object Failed : DashboardPing
}

sealed interface DashboardAction {
    data object ToggleService : DashboardAction
    data object TestPing : DashboardAction
    data object OpenConfigList : DashboardAction
    data object OpenExitChain : DashboardAction
    data class SetIranDirect(val enabled: Boolean) : DashboardAction
}

/** One-shot effects the activity must perform (they need an Activity or permission flow). */
sealed interface DashboardEffect {
    data object RestartService : DashboardEffect
}
