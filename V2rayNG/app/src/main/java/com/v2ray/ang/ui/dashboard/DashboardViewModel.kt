package com.v2ray.ang.ui.dashboard

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.ConnectionTestResult
import com.v2ray.ang.dto.TrafficSample
import com.v2ray.ang.extension.serializable
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.WarpSettings
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _effects = Channel<DashboardEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    /** Ping requests in flight; taps may overlap like the config list's connection test. */
    private val pingRequests = mutableSetOf<String>()
    private var autoPingDone = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val safeIntent = intent ?: return
            val requestId = safeIntent.getStringExtra(MessageHelper.EXTRA_REQUEST_ID)
            when (safeIntent.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING,
                AppConfig.MSG_STATE_START_SUCCESS -> onRunning(true)

                AppConfig.MSG_STATE_NOT_RUNNING,
                AppConfig.MSG_STATE_STOP_SUCCESS,
                AppConfig.MSG_STATE_START_FAILURE -> onRunning(false)

                AppConfig.MSG_TRAFFIC_SAMPLE ->
                    safeIntent.serializable<TrafficSample>("content")?.let(::onSample)

                AppConfig.MSG_MEASURE_DELAY_RESULT -> {
                    if (requestId == null || !pingRequests.remove(requestId)) return
                    safeIntent.serializable<ConnectionTestResult>("content")?.let(::onPingResult)
                }

                AppConfig.MSG_MEASURE_DELAY_CANCEL -> {
                    if (requestId == null || !pingRequests.remove(requestId)) return
                    _uiState.update { it.copy(ping = DashboardPing.Failed, pingsPending = pingRequests.size) }
                }
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            application,
            receiver,
            IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY),
            Utils.receiverFlags()
        )
        MessageHelper.sendMsg2Service(application, AppConfig.MSG_REGISTER_CLIENT, "")
        refreshConfig()
    }

    fun onAction(action: DashboardAction) {
        when (action) {
            DashboardAction.TestPing -> testPing()
            is DashboardAction.SetIranDirect -> setIranDirect(action.enabled)
            DashboardAction.ToggleService,
            DashboardAction.OpenExitChain,
            DashboardAction.OpenConfigList -> {
                // Handled by the activity: both need an Activity (VPN permission, navigation).
            }
        }
    }

    /** Reloads the selected config, which may have changed on the config list screen. */
    fun refreshConfig() {
        viewModelScope.launch {
            val (guid, profile, iran) = withContext(Dispatchers.IO) {
                val guid = MmkvManager.getSelectServer()
                Triple(
                    guid,
                    guid?.let { MmkvManager.decodeServerConfig(it) },
                    MmkvManager.decodeSettingsBool(AppConfig.PREF_IRAN_DIRECT, false)
                )
            }
            val chain = withContext(Dispatchers.IO) {
                WarpSettings.exitChain().takeIf { it.isOn && WarpSettings.modeFor(guid) == null }
            }
            _uiState.update {
                val changed = it.configGuid != guid
                it.copy(
                    configGuid = guid,
                    configName = profile?.remarks.orEmpty(),
                    configType = profile?.configType,
                    iranDirect = iran,
                    exitChain = chain,
                    ping = if (changed) DashboardPing.Idle else it.ping,
                )
            }
        }
    }

    private fun onRunning(running: Boolean) {
        val wasRunning = _uiState.value.isRunning
        _uiState.update { state ->
            if (running) state.copy(isRunning = true)
            else DashboardUiState(
                configGuid = state.configGuid,
                configName = state.configName,
                configType = state.configType,
                iranDirect = state.iranDirect,
            )
        }
        if (!running) {
            pingRequests.clear()
            autoPingDone = false
        } else if (!wasRunning && !autoPingDone) {
            // The exit IP and country come from the same probe, so run it once on connect.
            autoPingDone = true
            testPing()
        }
    }

    private fun onSample(sample: TrafficSample) {
        val down = (sample.proxyDownBps * 8 / 1_000_000).toFloat()
        val up = (sample.proxyUpBps * 8 / 1_000_000).toFloat()
        _uiState.update {
            it.copy(
                isRunning = true,
                downloadBps = sample.proxyDownBps,
                uploadBps = sample.proxyUpBps,
                sessionDownloadBytes = sample.sessionDownBytes,
                sessionUploadBytes = sample.sessionUpBytes,
                sessionStartMillis = sample.sessionStartMillis,
                downloadHistory = (it.downloadHistory + down).takeLast(DASHBOARD_HISTORY_SIZE),
                uploadHistory = (it.uploadHistory + up).takeLast(DASHBOARD_HISTORY_SIZE),
            )
        }
    }

    private fun testPing() {
        if (!_uiState.value.isRunning) return
        val requestId = UUID.randomUUID().toString()
        pingRequests.add(requestId)
        _uiState.update { it.copy(ping = DashboardPing.Testing, pingsPending = pingRequests.size) }
        MessageHelper.sendMsg2ServiceForResult(getApplication(), AppConfig.MSG_MEASURE_DELAY, requestId) { handled ->
            if (!handled && pingRequests.remove(requestId)) {
                _uiState.update { it.copy(ping = DashboardPing.Failed, pingsPending = pingRequests.size) }
            }
        }
    }

    private fun onPingResult(result: ConnectionTestResult) {
        _uiState.update {
            it.copy(
                pingsPending = pingRequests.size,
                ping = if (result.delayMillis >= 0) DashboardPing.Result(result.delayMillis) else DashboardPing.Failed,
                exitIp = result.ipAddress ?: it.exitIp,
                exitCountry = result.country ?: it.exitCountry,
            )
        }
    }

    private fun setIranDirect(enabled: Boolean) {
        _uiState.update { it.copy(iranDirect = enabled) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                MmkvManager.encodeSettings(AppConfig.PREF_IRAN_DIRECT, enabled)
            }
            // Routing is baked into the running core's config, so it only applies after a restart.
            if (_uiState.value.isRunning) _effects.send(DashboardEffect.RestartService)
        }
    }

    override fun onCleared() {
        runCatching { getApplication<Application>().unregisterReceiver(receiver) }
            .onFailure { LogUtil.e(AppConfig.TAG, "Dashboard: failed to unregister receiver", it) }
        super.onCleared()
    }
}
