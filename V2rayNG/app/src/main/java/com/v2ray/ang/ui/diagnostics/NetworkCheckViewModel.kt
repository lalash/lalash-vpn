package com.v2ray.ang.ui.diagnostics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.dto.CheckGroup
import com.v2ray.ang.dto.CheckItem
import com.v2ray.ang.dto.CheckStatus
import com.v2ray.ang.dto.NetworkConclusion
import com.v2ray.ang.dto.NetworkInfo
import com.v2ray.ang.handler.NetworkDiagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NetworkCheckUiState(
    val running: Boolean = false,
    /** Every row in display order; unfinished ones are PENDING. */
    val items: List<CheckItem> = emptyList(),
    val info: NetworkInfo? = null,
    val conclusion: NetworkConclusion? = null,
)

sealed interface NetworkCheckAction {
    data object Run : NetworkCheckAction
}

class NetworkCheckViewModel(application: Application) : AndroidViewModel(application) {

    private val diagnostics = NetworkDiagnostics(application)
    private val _uiState = MutableStateFlow(NetworkCheckUiState())
    val uiState: StateFlow<NetworkCheckUiState> = _uiState.asStateFlow()
    private var job: Job? = null

    init {
        run()
    }

    fun onAction(action: NetworkCheckAction) {
        when (action) {
            NetworkCheckAction.Run -> run()
        }
    }

    private fun run() {
        job?.cancel()
        _uiState.value = NetworkCheckUiState(running = true, items = placeholders())
        job = viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { diagnostics.networkInfo() }
            _uiState.update { it.copy(info = info) }
            val (_, conclusion) = diagnostics.run { item ->
                _uiState.update { state ->
                    state.copy(items = state.items.map { if (it.id == item.id) item else it })
                }
            }
            _uiState.update { state ->
                // Rows that never ran: network rows that do not apply (no router on mobile data)
                // are dropped, the rest (everything after "no network") are marked skipped.
                val items = state.items
                    .filterNot { it.status == CheckStatus.PENDING && it.group == CheckGroup.NETWORK }
                    .map { if (it.status == CheckStatus.PENDING) it.copy(status = CheckStatus.SKIPPED) else it }
                state.copy(running = false, items = items, conclusion = conclusion)
            }
        }
    }

    private fun placeholders(): List<CheckItem> = buildList {
        add(CheckItem("network", CheckGroup.NETWORK, "network"))
        add(CheckItem("gateway", CheckGroup.NETWORK, "gateway"))
        add(CheckItem("system_dns", CheckGroup.NETWORK, "system_dns"))
        NetworkDiagnostics.IRAN_SITES.forEach { add(CheckItem(it.id, CheckGroup.IRAN_SITES, it.label, it.host)) }
        NetworkDiagnostics.IRAN_DNS.forEach { add(CheckItem(it.id, CheckGroup.IRAN_DNS, it.label, it.host)) }
        NetworkDiagnostics.FOREIGN_SITES.forEach { add(CheckItem(it.id, CheckGroup.FOREIGN_SITES, it.label, it.host)) }
        NetworkDiagnostics.FOREIGN_DNS.forEach { add(CheckItem(it.id, CheckGroup.FOREIGN_DNS, it.label, it.host)) }
    }
}
