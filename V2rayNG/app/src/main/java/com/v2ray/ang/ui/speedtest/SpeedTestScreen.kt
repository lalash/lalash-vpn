package com.v2ray.ang.ui.speedtest

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.dto.SpeedTestMode
import com.v2ray.ang.dto.SpeedTestState
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.QRCodeDialog
import com.v2ray.ang.ui.compose.SelectListDialog
import com.v2ray.ang.ui.compose.colorConfigType
import com.v2ray.ang.ui.compose.colorFabActive
import com.v2ray.ang.ui.compose.colorOnFabActive
import com.v2ray.ang.ui.compose.colorPing
import java.util.Locale

private val speedGreen = Color(0xFF00875A)
private val speedLightGreen = Color(0xFF3D8F12)
private val speedYellow = Color(0xFFA07B00)
private val speedOrange = Color(0xFFC75F08)
private val speedRed = Color(0xFFD50000)

/** Same thresholds as the desktop tester: >=20, >=8, >=3, >=1, below 1 Mbps. */
private fun speedColor(mbps: Double): Color = when {
    mbps >= 20 -> speedGreen
    mbps >= 8 -> speedLightGreen
    mbps >= 3 -> speedYellow
    mbps >= 1 -> speedOrange
    else -> speedRed
}

private fun pingColor(ms: Long): Color = when {
    ms < 300 -> speedGreen
    ms < 700 -> speedOrange
    else -> speedRed
}

private enum class ShareOption(val labelRes: Int) {
    QR(R.string.share_method_qrcode),
    LINK(R.string.share_method_clipboard),
    FULL(R.string.share_method_full_content),
}

@Composable
fun SpeedTestScreen(
    state: SpeedTestUiState,
    onAction: (SpeedTestAction) -> Unit,
    onOpenSettings: () -> Unit,
    readClipboard: () -> String,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.speedtest_title),
                onBackClick = onBack,
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(R.drawable.ic_settings_24dp), contentDescription = stringResource(R.string.speedtest_settings_title))
                    }
                }
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val hasResults = state.rows.any { it.result.state == SpeedTestState.DONE }
                    OutlinedButton(
                        onClick = { onAction(SpeedTestAction.CopyTop10) },
                        enabled = hasResults,
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.speedtest_copy_top)) }
                    Button(
                        onClick = { onAction(SpeedTestAction.AddTopToGroup) },
                        enabled = hasResults,
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.speedtest_add_top_to_group)) }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item(key = "modes") { ModeChips(state.mode) { onAction(SpeedTestAction.SelectMode(it)) } }
            item(key = "source") { SourceCard(state, onAction) }
            item(key = "progress") { ProgressBlock(state) }
            item(key = "header") { TableHeader(state.sort) { onAction(SpeedTestAction.CycleSort) } }
            items(state.rows, key = { it.guid }) { row ->
                ResultRow(row, state.mode) { onAction(SpeedTestAction.OpenShare(row.guid)) }
                ItemDivider()
            }
        }
    }

    state.shareTarget?.let { target ->
        SelectListDialog(
            options = ShareOption.entries,
            optionText = { stringResource(it.labelRes) },
            title = target.name,
            onSelected = { option ->
                when (option) {
                    ShareOption.QR -> onAction(SpeedTestAction.ShareQr(target.guid))
                    ShareOption.LINK -> onAction(SpeedTestAction.CopyLink(target.guid))
                    ShareOption.FULL -> onAction(SpeedTestAction.CopyFull(target.guid))
                }
            },
            onDismiss = { onAction(SpeedTestAction.DismissShare) }
        )
    }
    state.qrBitmap?.let { QRCodeDialog(bitmap = it, onDismiss = { onAction(SpeedTestAction.DismissQr) }) }
    if (state.showManualDialog) {
        ManualImportDialog(
            isImporting = state.isImporting,
            readClipboard = readClipboard,
            onImport = { onAction(SpeedTestAction.ImportManual(it)) },
            onDismiss = { onAction(SpeedTestAction.DismissManual) }
        )
    }
}

@Composable
private fun ModeChips(selected: SpeedTestMode, onSelect: (SpeedTestMode) -> Unit) {
    val labels = mapOf(
        SpeedTestMode.UPLOAD_DOWNLOAD to R.string.speedtest_mode_both,
        SpeedTestMode.UPLOAD_ONLY to R.string.speedtest_mode_upload,
        SpeedTestMode.DOWNLOAD_ONLY to R.string.speedtest_mode_download,
        SpeedTestMode.PING_ONLY to R.string.speedtest_mode_ping,
    )
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SpeedTestMode.entries.forEach { mode ->
            FilterChip(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                label = { Text(stringResource(labels.getValue(mode))) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
        }
    }
}

@Composable
private fun SourceCard(state: SpeedTestUiState, onAction: (SpeedTestAction) -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.speedtest_select_for_test), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.speedtest_total_selected, state.selectedCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.sources.forEach { source ->
                    FilterChip(
                        selected = source.id in state.selectedSources,
                        onClick = { onAction(SpeedTestAction.ToggleSource(source.id)) },
                        enabled = !state.isRunning,
                        label = { Text("${source.name.ifEmpty { stringResource(R.string.speedtest_default_group) }} · ${source.count}") },
                        leadingIcon = if (source.id in state.selectedSources) {
                            { Icon(painterResource(R.drawable.ic_action_done), contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.onSurface,
                            selectedLabelColor = MaterialTheme.colorScheme.surface,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = { onAction(SpeedTestAction.OpenManual) },
                    enabled = !state.isRunning,
                    label = { Text(stringResource(R.string.speedtest_manual_input), color = colorConfigType, fontWeight = FontWeight.SemiBold) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_add_24dp), contentDescription = null, tint = colorConfigType, modifier = Modifier.size(16.dp)) },
                    border = BorderStroke(1.dp, colorFabActive)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onAction(SpeedTestAction.StartOrStop) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isRunning) MaterialTheme.colorScheme.onSurface else colorFabActive,
                        contentColor = if (state.isRunning) MaterialTheme.colorScheme.surface else colorOnFabActive
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                ) {
                    Icon(painterResource(if (state.isRunning) R.drawable.ic_stop_24dp else R.drawable.ic_play_24dp), contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (state.isRunning) R.string.speedtest_stop else R.string.speedtest_start), fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.speedtest_top_n), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onAction(SpeedTestAction.ChangeTopN(5)) }, enabled = !state.isRunning, modifier = Modifier.size(36.dp)) {
                            Icon(painterResource(R.drawable.ic_add_24dp), contentDescription = stringResource(R.string.speedtest_increase))
                        }
                        Text("${state.topN}", fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
                        IconButton(onClick = { onAction(SpeedTestAction.ChangeTopN(-5)) }, enabled = !state.isRunning, modifier = Modifier.size(36.dp)) {
                            Icon(painterResource(R.drawable.ic_remove_24dp), contentDescription = stringResource(R.string.speedtest_decrease))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressBlock(state: SpeedTestUiState) {
    if (state.pingTotal == 0) return
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PhaseProgress(stringResource(R.string.speedtest_phase_ping), state.pingDone, state.pingTotal, colorPing, Modifier.weight(1f))
            PhaseProgress(stringResource(R.string.speedtest_phase_speed), state.speedDone, state.speedTotal, colorFabActive, Modifier.weight(1f))
        }
        val current = state.rows.firstOrNull { it.guid == state.currentGuid }
        val line = when {
            current != null -> {
                val live = current.result.liveMbps?.let { "%.1f Mbps".format(Locale.US, it) }
                stringResource(R.string.speedtest_current, current.name) + (live?.let { " · $it" } ?: "")
            }
            state.isRunning -> stringResource(R.string.speedtest_pinging)
            else -> stringResource(R.string.speedtest_finished)
        }
        Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PhaseProgress(title: String, done: Int, total: Int, color: Color, modifier: Modifier) {
    Column(modifier) {
        Row {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("$done / $total", style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Ltr), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = { if (total > 0) done.toFloat() / total else 0f },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private val columnWidth = 46.dp

@Composable
private fun TableHeader(sort: SpeedTestSort, onCycleSort: () -> Unit) {
    val sortLabel = stringResource(
        when (sort) {
            SpeedTestSort.UPLOAD -> R.string.speedtest_col_upload
            SpeedTestSort.DOWNLOAD -> R.string.speedtest_col_download
            SpeedTestSort.PING -> R.string.speedtest_col_ping
            SpeedTestSort.JITTER -> R.string.speedtest_col_jitter
        }
    )
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onCycleSort, modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.speedtest_sort_by, sortLabel),
                color = colorConfigType,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
        HeaderCell(stringResource(R.string.speedtest_col_ping), "ms")
        HeaderCell(stringResource(R.string.speedtest_col_jitter), "ms")
        // Arrows only: the full words do not fit a 46dp column; the name stays in semantics.
        HeaderCell("↑", "Mbps", stringResource(R.string.speedtest_col_upload))
        HeaderCell("↓", "Mbps", stringResource(R.string.speedtest_col_download))
        Spacer(Modifier.width(40.dp))
    }
}

@Composable
private fun HeaderCell(title: String, unit: String, description: String = title) {
    Column(
        Modifier
            .width(columnWidth)
            .clearAndSetSemantics { contentDescription = "$description $unit" },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Clip)
        Text(unit, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Clip)
    }
}

@Composable
private fun ResultRow(row: SpeedTestRow, mode: SpeedTestMode, onShare: () -> Unit) {
    val r = row.result
    val muted = MaterialTheme.colorScheme.outline
    val (stateLabel, stateColor) = when (r.state) {
        SpeedTestState.DONE -> stringResource(R.string.speedtest_state_done) to MaterialTheme.colorScheme.onSurfaceVariant
        SpeedTestState.TESTING -> stringResource(R.string.speedtest_state_testing) to speedOrange
        SpeedTestState.PINGING -> stringResource(R.string.speedtest_state_pinging) to speedOrange
        SpeedTestState.QUEUED -> stringResource(R.string.speedtest_state_queued) to muted
        SpeedTestState.SKIPPED -> stringResource(R.string.speedtest_state_skipped) to muted
        SpeedTestState.DEAD -> stringResource(R.string.speedtest_state_dead) to speedRed
    }
    val stripColor = when (r.state) {
        SpeedTestState.DEAD -> speedRed
        SpeedTestState.TESTING, SpeedTestState.PINGING -> colorFabActive
        SpeedTestState.QUEUED, SpeedTestState.SKIPPED ->
            if (r.pingMs != null) pingColor(r.pingMs).copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant
        SpeedTestState.DONE -> when (mode) {
            SpeedTestMode.PING_ONLY -> r.pingMs?.let(::pingColor) ?: muted
            SpeedTestMode.DOWNLOAD_ONLY -> r.downloadMbps?.let(::speedColor) ?: muted
            else -> r.uploadMbps?.let(::speedColor) ?: muted
        }
    }
    val dead = r.state == SpeedTestState.DEAD
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .background(
                if (r.state == SpeedTestState.TESTING || r.state == SpeedTestState.PINGING) colorFabActive.copy(alpha = 0.08f)
                else Color.Transparent
            )
            .padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .padding(vertical = 6.dp)
                .width(6.dp)
                .height(42.dp)
                .background(stripColor, RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 10.dp)
                .semantics(mergeDescendants = true) {}
        ) {
            Text(row.name.ifEmpty { "—" }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row {
                row.configType?.let { Text(it.name, fontSize = 11.sp, color = colorConfigType, fontWeight = FontWeight.SemiBold) }
                Text(" · $stateLabel", fontSize = 11.sp, color = stateColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // Ping and jitter come from phase 1 and stay as measured; a speed failure later does not hide them.
        ValueCell(r.pingMs?.toString(), r.pingMs?.let(::pingColor), dead)
        ValueCell(r.jitterMs?.toString(), MaterialTheme.colorScheme.onSurfaceVariant, dead)
        val noSpeed = r.state == SpeedTestState.SKIPPED
        ValueCell(
            if (!mode.measuresUpload || noSpeed) "—" else r.uploadMbps?.let { "%.1f".format(Locale.US, it) },
            r.uploadMbps?.let(::speedColor), dead
        )
        ValueCell(
            if (!mode.measuresDownload || noSpeed) "—" else r.downloadMbps?.let { "%.1f".format(Locale.US, it) },
            r.downloadMbps?.let(::speedColor), dead
        )
        IconButton(onClick = onShare, modifier = Modifier.size(40.dp)) {
            Icon(
                painterResource(R.drawable.ic_share_24dp),
                contentDescription = stringResource(R.string.speedtest_share_config, row.name),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ValueCell(value: String?, color: Color?, dead: Boolean) {
    Text(
        value ?: if (dead) "×" else "…",
        modifier = Modifier.width(columnWidth),
        textAlign = TextAlign.Center,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = when {
            value == null && dead -> speedRed
            value == null || value == "—" -> MaterialTheme.colorScheme.outline
            else -> color ?: MaterialTheme.colorScheme.onSurface
        },
        maxLines = 1,
        style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr)
    )
}

@Composable
private fun ManualImportDialog(
    isImporting: Boolean,
    readClipboard: () -> String,
    onImport: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val lines = text.lines().count { it.contains("://") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.speedtest_manual_title)) },
        text = {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.speedtest_manual_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.speedtest_manual_links)) },
                    textStyle = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                    minLines = 6,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { text = readClipboard() }) { Text(stringResource(R.string.speedtest_from_clipboard)) }
                    Spacer(Modifier.weight(1f))
                    if (lines > 0) Text(stringResource(R.string.speedtest_links_detected, lines), style = MaterialTheme.typography.bodySmall, color = colorPing)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onImport(text) }, enabled = text.isNotBlank() && !isImporting) {
                Text(stringResource(R.string.speedtest_manual_add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } }
    )
}
