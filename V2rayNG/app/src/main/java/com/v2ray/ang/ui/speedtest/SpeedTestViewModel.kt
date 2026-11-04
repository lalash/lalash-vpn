package com.v2ray.ang.ui.speedtest

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.SpeedTestMode
import com.v2ray.ang.dto.SpeedTestRequest
import com.v2ray.ang.dto.SpeedTestResult
import com.v2ray.ang.dto.SpeedTestState
import com.v2ray.ang.dto.SpeedTestUpdate
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.serializable
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SpeedTestSettings
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.service.SpeedTestService
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

enum class SpeedTestSort { UPLOAD, DOWNLOAD, PING, JITTER }

data class SourceOption(val id: String, val name: String, val count: Int)

data class SpeedTestRow(
    val guid: String,
    val name: String,
    val configType: EConfigType?,
    val result: SpeedTestResult,
)

data class SpeedTestUiState(
    val mode: SpeedTestMode = SpeedTestMode.UPLOAD_DOWNLOAD,
    val topN: Int = SpeedTestSettings.DEFAULT_TOP_N,
    val sources: List<SourceOption> = emptyList(),
    val selectedSources: Set<String> = emptySet(),
    val isRunning: Boolean = false,
    val pingDone: Int = 0,
    val pingTotal: Int = 0,
    val speedDone: Int = 0,
    val speedTotal: Int = 0,
    val currentGuid: String? = null,
    val rows: List<SpeedTestRow> = emptyList(),
    val sort: SpeedTestSort = SpeedTestSort.UPLOAD,
    val shareTarget: SpeedTestRow? = null,
    val qrBitmap: Bitmap? = null,
    val showManualDialog: Boolean = false,
    val isImporting: Boolean = false,
) {
    val selectedCount: Int get() = sources.filter { it.id in selectedSources }.sumOf { it.count }
}

sealed interface SpeedTestAction {
    data class SelectMode(val mode: SpeedTestMode) : SpeedTestAction
    data class ChangeTopN(val delta: Int) : SpeedTestAction
    data class ToggleSource(val id: String) : SpeedTestAction
    data object StartOrStop : SpeedTestAction
    data object CycleSort : SpeedTestAction
    data class OpenShare(val guid: String) : SpeedTestAction
    data object DismissShare : SpeedTestAction
    data class ShareQr(val guid: String) : SpeedTestAction
    data object DismissQr : SpeedTestAction
    data class CopyLink(val guid: String) : SpeedTestAction
    data class CopyFull(val guid: String) : SpeedTestAction
    data object OpenManual : SpeedTestAction
    data object DismissManual : SpeedTestAction
    data class ImportManual(val text: String) : SpeedTestAction
    data object CopyTop10 : SpeedTestAction
    data object AddTopToGroup : SpeedTestAction
}

/** Messages for the activity to show; resolved to strings there. */
sealed interface SpeedTestMessage {
    data class Success(val resId: Int, val count: Int? = null) : SpeedTestMessage
    data class Failure(val resId: Int) : SpeedTestMessage
}

class SpeedTestViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SpeedTestUiState())
    val uiState: StateFlow<SpeedTestUiState> = _uiState.asStateFlow()

    private val _messages = Channel<SpeedTestMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var requestId: String? = null
    private val results = LinkedHashMap<String, SpeedTestResult>()
    private val profiles = HashMap<String, Pair<String, EConfigType?>>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val safeIntent = intent ?: return
            val id = safeIntent.getStringExtra(MessageHelper.EXTRA_REQUEST_ID)
            if (id == null || id != requestId) return
            when (safeIntent.getIntExtra("key", 0)) {
                AppConfig.MSG_SPEEDTEST_UPDATE ->
                    safeIntent.serializable<SpeedTestUpdate>("content")?.let(::onUpdate)

                AppConfig.MSG_SPEEDTEST_FINISH -> {
                    requestId = null
                    _uiState.update { it.copy(isRunning = false, currentGuid = null) }
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
        _uiState.update {
            it.copy(
                mode = SpeedTestMode.fromOrdinal(MmkvManager.decodeSettingsInt(AppConfig.PREF_ST_MODE, 0)),
                topN = MmkvManager.decodeSettingsInt(AppConfig.PREF_ST_TOP_N, SpeedTestSettings.DEFAULT_TOP_N),
            )
        }
        loadSources()
    }

    fun onAction(action: SpeedTestAction) {
        when (action) {
            is SpeedTestAction.SelectMode -> {
                MmkvManager.encodeSettings(AppConfig.PREF_ST_MODE, action.mode.ordinal)
                _uiState.update { it.copy(mode = action.mode) }
                rebuildRows()
            }

            is SpeedTestAction.ChangeTopN -> {
                val next = (_uiState.value.topN + action.delta).coerceIn(1, 500)
                MmkvManager.encodeSettings(AppConfig.PREF_ST_TOP_N, next)
                _uiState.update { it.copy(topN = next) }
            }

            is SpeedTestAction.ToggleSource -> _uiState.update {
                val selected = it.selectedSources.toMutableSet()
                if (!selected.add(action.id)) selected.remove(action.id)
                it.copy(selectedSources = selected)
            }

            SpeedTestAction.StartOrStop -> if (_uiState.value.isRunning) stop() else start()
            SpeedTestAction.CycleSort -> {
                _uiState.update { it.copy(sort = SpeedTestSort.entries[(it.sort.ordinal + 1) % SpeedTestSort.entries.size]) }
                rebuildRows()
            }

            is SpeedTestAction.OpenShare -> _uiState.update { state ->
                state.copy(shareTarget = state.rows.firstOrNull { it.guid == action.guid })
            }

            SpeedTestAction.DismissShare -> _uiState.update { it.copy(shareTarget = null) }
            is SpeedTestAction.ShareQr -> viewModelScope.launch {
                _uiState.update { it.copy(shareTarget = null) }
                val bitmap = withContext(Dispatchers.Default) { AngConfigManager.share2QRCode(action.guid) }
                if (bitmap == null) _messages.send(SpeedTestMessage.Failure(R.string.toast_failure))
                _uiState.update { it.copy(qrBitmap = bitmap) }
            }

            SpeedTestAction.DismissQr -> _uiState.update { it.copy(qrBitmap = null) }
            is SpeedTestAction.CopyLink -> viewModelScope.launch {
                _uiState.update { it.copy(shareTarget = null) }
                val ok = withContext(Dispatchers.IO) { AngConfigManager.share2Clipboard(getApplication(), action.guid) == 0 }
                _messages.send(if (ok) SpeedTestMessage.Success(R.string.toast_success) else SpeedTestMessage.Failure(R.string.toast_failure))
            }

            is SpeedTestAction.CopyFull -> viewModelScope.launch {
                _uiState.update { it.copy(shareTarget = null) }
                val ok = withContext(Dispatchers.IO) { AngConfigManager.shareFullContent2Clipboard(getApplication(), action.guid) == 0 }
                _messages.send(if (ok) SpeedTestMessage.Success(R.string.toast_success) else SpeedTestMessage.Failure(R.string.toast_failure))
            }

            SpeedTestAction.OpenManual -> _uiState.update { it.copy(showManualDialog = true) }
            SpeedTestAction.DismissManual -> _uiState.update { it.copy(showManualDialog = false) }
            is SpeedTestAction.ImportManual -> importManual(action.text)
            SpeedTestAction.CopyTop10 -> copyTop10()
            SpeedTestAction.AddTopToGroup -> addTopToGroup()
        }
    }

    /** Selects only [groupId] as the test source (used when opened from a group's speed test button). */
    fun preselectSource(groupId: String) {
        _uiState.update { it.copy(selectedSources = setOf(groupId)) }
    }

    /** Lists every subscription group with its config count. */
    fun loadSources() {
        viewModelScope.launch {
            val sources = withContext(Dispatchers.IO) {
                MmkvManager.decodeSubscriptions().map { sub ->
                    SourceOption(
                        id = sub.guid,
                        name = sub.subscription.remarks,
                        count = MmkvManager.decodeServerList(sub.guid).size,
                    )
                }.filter { it.count > 0 }
            }
            _uiState.update { state ->
                val valid = state.selectedSources.filter { id -> sources.any { it.id == id } }.toSet()
                state.copy(sources = sources, selectedSources = valid)
            }
        }
    }

    private fun start() {
        val state = _uiState.value
        if (state.selectedSources.isEmpty()) {
            viewModelScope.launch { _messages.send(SpeedTestMessage.Failure(R.string.speedtest_select_source)) }
            return
        }
        viewModelScope.launch {
            val guids = withContext(Dispatchers.IO) {
                val list = state.selectedSources.flatMap { MmkvManager.decodeServerList(it) }.distinct()
                profiles.clear()
                list.forEach { guid ->
                    val profile = MmkvManager.decodeServerConfig(guid)
                    profiles[guid] = (profile?.remarks.orEmpty()) to profile?.configType
                }
                list
            }
            if (guids.isEmpty()) {
                _messages.send(SpeedTestMessage.Failure(R.string.speedtest_select_source))
                return@launch
            }
            results.clear()
            guids.forEach { results[it] = SpeedTestResult(guid = it, state = SpeedTestState.QUEUED) }
            val id = UUID.randomUUID().toString()
            requestId = id
            _uiState.update {
                it.copy(
                    isRunning = true, pingDone = 0, pingTotal = guids.size,
                    speedDone = 0, speedTotal = 0, currentGuid = null,
                )
            }
            rebuildRows()
            SpeedTestService.start(getApplication(), SpeedTestRequest(guids, state.mode, state.topN), id)
        }
    }

    private fun stop() {
        requestId = null
        SpeedTestService.cancel(getApplication())
        _uiState.update { it.copy(isRunning = false, currentGuid = null) }
    }

    private fun onUpdate(update: SpeedTestUpdate) {
        update.result?.let { results[it.guid] = it }
        _uiState.update {
            it.copy(
                pingDone = update.pingDone,
                pingTotal = update.pingTotal,
                speedDone = update.speedDone,
                speedTotal = update.speedTotal,
                currentGuid = update.currentGuid,
            )
        }
        rebuildRows()
    }

    private fun rebuildRows() {
        val sort = _uiState.value.sort
        val mode = _uiState.value.mode
        val rows = results.values.map { result ->
            val (name, type) = profiles[result.guid] ?: ("" to null)
            SpeedTestRow(result.guid, name, type, result)
        }.sortedWith(rowComparator(sort, mode))
        _uiState.update { it.copy(rows = rows) }
    }

    private fun copyTop10() {
        viewModelScope.launch {
            val top = topGuids(10)
            val count = withContext(Dispatchers.IO) {
                AngConfigManager.shareNonCustomConfigsToClipboard(getApplication(), top)
            }
            _messages.send(
                if (count > 0) SpeedTestMessage.Success(R.string.speedtest_copied_count, count)
                else SpeedTestMessage.Failure(R.string.toast_failure)
            )
        }
    }

    private fun addTopToGroup() {
        viewModelScope.launch {
            val top = topGuids(10)
            val count = withContext(Dispatchers.IO) {
                // One link per config, so each imported copy can be matched to its source.
                val withLinks = top.mapNotNull { guid -> AngConfigManager.shareLinks(listOf(guid)).takeIf { it.isNotEmpty() }?.let { guid to it } }
                if (withLinks.isEmpty()) return@withContext 0
                val subId = findOrCreateSubscription(AppConfig.ST_TOP_SUBSCRIPTION_REMARKS)
                val imported = AngConfigManager.importBatchConfig(withLinks.joinToString("\n") { it.second }, subId, append = false).first
                // The copies are new configs; carry the ping, jitter and speeds over so the list shows them.
                val copies = MmkvManager.decodeServerList(subId)
                if (copies.size == withLinks.size) {
                    copies.forEachIndexed { i, copy -> MmkvManager.copyServerAffiliationInfo(withLinks[i].first, copy) }
                }
                imported
            }
            _messages.send(
                if (count > 0) SpeedTestMessage.Success(R.string.speedtest_added_to_group, count)
                else SpeedTestMessage.Failure(R.string.toast_failure)
            )
            loadSources()
        }
    }

    private fun importManual(text: String) {
        if (text.isBlank()) return
        _uiState.update { it.copy(isImporting = true) }
        viewModelScope.launch {
            val (subId, count) = withContext(Dispatchers.IO) {
                val subId = findOrCreateSubscription(AppConfig.ST_MANUAL_SUBSCRIPTION_REMARKS)
                subId to try {
                    AngConfigManager.importBatchConfig(text, subId, append = false).first
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "SpeedTest: manual import failed", e)
                    0
                }
            }
            _uiState.update { it.copy(isImporting = false, showManualDialog = count <= 0) }
            if (count > 0) {
                _messages.send(SpeedTestMessage.Success(R.string.speedtest_imported_count, count))
                loadSources()
                _uiState.update { it.copy(selectedSources = it.selectedSources + subId) }
            } else {
                _messages.send(SpeedTestMessage.Failure(R.string.speedtest_import_none))
            }
        }
    }

    private fun topGuids(limit: Int): List<String> =
        _uiState.value.rows.filter { it.result.state == SpeedTestState.DONE }.take(limit).map { it.guid }

    private fun findOrCreateSubscription(remarks: String): String {
        MmkvManager.decodeSubscriptions().firstOrNull { it.subscription.remarks == remarks }?.let { return it.guid }
        val guid = Utils.getUuid()
        MmkvManager.encodeSubscription(guid, SubscriptionItem(remarks = remarks), markNew = false)
        return guid
    }

    override fun onCleared() {
        runCatching { getApplication<Application>().unregisterReceiver(receiver) }
            .onFailure { LogUtil.e(AppConfig.TAG, "SpeedTest: failed to unregister receiver", it) }
        super.onCleared()
    }

    companion object {
        /**
         * Tested rows first (best first by [sort]), then the one under test, then pinging/queued,
         * then not-tested-but-alive, then dead. Ties fall back to the lowest ping.
         */
        internal fun rowComparator(sort: SpeedTestSort, mode: SpeedTestMode): Comparator<SpeedTestRow> {
            val order = mapOf(
                SpeedTestState.DONE to 0, SpeedTestState.TESTING to 1, SpeedTestState.PINGING to 2,
                SpeedTestState.QUEUED to 2, SpeedTestState.SKIPPED to 3, SpeedTestState.DEAD to 4,
            )
            val effectiveSort = when {
                mode == SpeedTestMode.PING_ONLY && (sort == SpeedTestSort.UPLOAD || sort == SpeedTestSort.DOWNLOAD) -> SpeedTestSort.PING
                mode == SpeedTestMode.DOWNLOAD_ONLY && sort == SpeedTestSort.UPLOAD -> SpeedTestSort.DOWNLOAD
                mode == SpeedTestMode.UPLOAD_ONLY && sort == SpeedTestSort.DOWNLOAD -> SpeedTestSort.UPLOAD
                else -> sort
            }
            return compareBy<SpeedTestRow> { order[it.result.state] ?: 5 }.thenBy { row ->
                val r = row.result
                when (effectiveSort) {
                    SpeedTestSort.UPLOAD -> -(r.uploadMbps ?: -1.0)
                    SpeedTestSort.DOWNLOAD -> -(r.downloadMbps ?: -1.0)
                    SpeedTestSort.PING -> (r.pingMs ?: Long.MAX_VALUE).toDouble()
                    SpeedTestSort.JITTER -> (r.jitterMs ?: Long.MAX_VALUE).toDouble()
                }
            }.thenBy { it.result.pingMs ?: Long.MAX_VALUE }
        }
    }
}
