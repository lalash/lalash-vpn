package com.v2ray.ang.ui.usage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.TrafficUsageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

enum class UsageRange { TODAY, WEEK, MONTH }

data class UsageBar(val label: String, val up: Long, val down: Long) {
    val total: Long get() = up + down
}

data class ConfigUsage(
    val guid: String,
    val name: String,
    val configType: EConfigType?,
    val up: Long,
    val down: Long,
) {
    val total: Long get() = up + down
}

data class UsageUiState(
    val range: UsageRange = UsageRange.WEEK,
    val isLoading: Boolean = true,
    val totalUp: Long = 0,
    val totalDown: Long = 0,
    val bars: List<UsageBar> = emptyList(),
    val configs: List<ConfigUsage> = emptyList(),
)

sealed interface UsageAction {
    data class SelectRange(val range: UsageRange) : UsageAction
}

class UsageViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(UsageUiState())
    val uiState: StateFlow<UsageUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init {
        reload()
    }

    fun onAction(action: UsageAction) {
        when (action) {
            is UsageAction.SelectRange -> {
                _uiState.update { it.copy(range = action.range) }
                reload()
            }
        }
    }

    fun reload() {
        val range = _uiState.value.range
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val loaded = withContext(Dispatchers.IO) { load(range) }
            _uiState.update { loaded.copy(range = it.range, isLoading = false) }
        }
    }

    private fun load(range: UsageRange): UsageUiState {
        val locale = Locale.getDefault()
        val today = LocalDate.now()
        val (bars, from) = when (range) {
            UsageRange.TODAY -> {
                val hours = TrafficUsageStore.hourlyToday()
                // 24 hourly values grouped into six 4-hour bars.
                hours.chunked(4).mapIndexed { i, chunk ->
                    UsageBar(
                        label = "%d".format(locale, i * 4),
                        up = chunk.sumOf { it.up },
                        down = chunk.sumOf { it.down },
                    )
                } to today
            }

            UsageRange.WEEK -> TrafficUsageStore.dailyTotals(7).map { (date, usage) ->
                UsageBar(date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale), usage.up, usage.down)
            } to today.minusDays(6)

            UsageRange.MONTH -> {
                val formatter = DateTimeFormatter.ofPattern("MM/dd", locale)
                // 30 days in five 6-day bars, labelled by each bar's first day.
                TrafficUsageStore.dailyTotals(30).chunked(6).map { chunk ->
                    UsageBar(
                        label = chunk.first().first.format(formatter),
                        up = chunk.sumOf { it.second.up },
                        down = chunk.sumOf { it.second.down },
                    )
                } to today.minusDays(29)
            }
        }
        val configs = TrafficUsageStore.perConfig(from)
            .filterValues { it.total > 0 }
            .map { (guid, usage) ->
                val profile = guid.takeIf { it.isNotEmpty() }?.let { MmkvManager.decodeServerConfig(it) }
                ConfigUsage(
                    guid = guid,
                    name = profile?.remarks.orEmpty(),
                    configType = profile?.configType,
                    up = usage.up,
                    down = usage.down,
                )
            }
            .sortedByDescending { it.total }
        return UsageUiState(
            range = range,
            totalUp = bars.sumOf { it.up },
            totalDown = bars.sumOf { it.down },
            bars = bars,
            configs = configs,
        )
    }
}
