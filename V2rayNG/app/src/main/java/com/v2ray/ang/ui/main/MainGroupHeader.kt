package com.v2ray.ang.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.dto.GroupMapItem
import com.v2ray.ang.ui.compose.colorConfigType
import com.v2ray.ang.ui.compose.colorFabActive
import com.v2ray.ang.ui.compose.colorOnFabActive

/** Row above a group's config list: count, sort, and for subscriptions edit + speed test. */
@Composable
internal fun GroupHeaderBar(
    group: GroupMapItem?,
    showGroupActions: Boolean,
    rowCount: Int,
    sort: MainSort,
    onCycleSort: () -> Unit,
    onEdit: () -> Unit,
    onSpeedTest: () -> Unit,
    onPing: () -> Unit = {},
    isTesting: Boolean = false,
) {
    val isSubscription = group != null && group.url.isNotEmpty()
    Column(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (showGroupActions && group != null && rowCount > 0) {
                Button(
                    onClick = onSpeedTest,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colorFabActive, contentColor = colorOnFabActive),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(painterResource(R.drawable.ic_speed_24dp), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.main_speed_test), fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onPing,
                    enabled = !isTesting,
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 10.dp, end = 12.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(painterResource(R.drawable.ic_ping_24dp), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.main_ping_now), fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
            if (showGroupActions && isSubscription) {
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(
                        painterResource(R.drawable.ic_edit_24dp),
                        contentDescription = stringResource(R.string.sub_edit_title),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.main_config_count, rowCount),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onCycleSort,
                shape = RoundedCornerShape(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(stringResource(R.string.main_sort_prefix), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(sortLabel(sort)), style = MaterialTheme.typography.labelMedium, color = colorConfigType, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        val autoLine = group?.let { groupAutoLine(it) }.orEmpty()
        if (showGroupActions && isSubscription && autoLine.isNotEmpty()) {
            Text(
                autoLine,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )
        }
    }
}

@Composable
internal fun groupAutoLine(group: GroupMapItem): String {
    val parts = mutableListOf<String>()
    if (group.autoUpdate) parts += stringResource(R.string.sub_auto_update_every, (group.updateIntervalMinutes / 60).coerceAtLeast(1))
    if (group.autoPingMinutes > 0) parts += stringResource(R.string.sub_auto_ping_every, group.autoPingMinutes)
    return parts.joinToString(" · ")
}

internal fun sortLabel(sort: MainSort): Int = when (sort) {
    MainSort.SUBSCRIPTION -> R.string.main_sort_subscription
    MainSort.PING -> R.string.main_sort_ping
    MainSort.JITTER -> R.string.main_sort_jitter
    MainSort.DOWNLOAD -> R.string.main_sort_download
    MainSort.UPLOAD -> R.string.main_sort_upload
    MainSort.NAME -> R.string.main_sort_name
}

/** Shown instead of the list while a new subscription's configs are still being fetched. */
@Composable
internal fun NewSubscriptionLoader(fetch: NewSubFetch, onRetry: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, colorFabActive.copy(alpha = 0.6f)),
        color = colorFabActive.copy(alpha = 0.06f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!fetch.failed) CircularProgressIndicator(color = colorFabActive, modifier = Modifier.size(36.dp))
            Text(
                stringResource(if (fetch.failed) R.string.main_sub_fetch_failed else R.string.main_sub_fetching),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(R.string.main_sub_fetch_attempt, fetch.attempt, fetch.maxAttempts),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onRetry, shape = RoundedCornerShape(4.dp)) {
                Text(stringResource(R.string.main_sub_retry_now))
            }
        }
    }
}

/** Edit a subscription: name, link, auto update interval, auto ping interval. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubscriptionEditSheet(group: GroupMapItem, onDismiss: () -> Unit, onSave: (MainAction.SaveSubscription) -> Unit) {
    var name by rememberSaveable(group.id) { mutableStateOf(group.remarks) }
    var url by rememberSaveable(group.id) { mutableStateOf(group.url) }
    var autoUpdate by rememberSaveable(group.id) { mutableStateOf(group.autoUpdate) }
    var hours by rememberSaveable(group.id) { mutableIntStateOf((group.updateIntervalMinutes / 60).toInt().coerceIn(1, 72)) }
    var autoPing by rememberSaveable(group.id) { mutableStateOf(group.autoPingMinutes > 0) }
    var minutes by rememberSaveable(group.id) { mutableIntStateOf(group.autoPingMinutes.takeIf { it > 0 } ?: 5) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(stringResource(R.string.sub_edit_title), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text(stringResource(R.string.sub_edit_name)) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = url, onValueChange = { url = it },
                label = { Text(stringResource(R.string.sub_edit_url)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
            SwitchRow(stringResource(R.string.sub_edit_auto_update), stringResource(R.string.sub_edit_auto_update_summary), autoUpdate) { autoUpdate = it }
            if (autoUpdate) {
                Stepper(stringResource(R.string.sub_edit_every_hours), stringResource(R.string.sub_edit_hours_value, hours), { hours = (hours - 1).coerceAtLeast(1) }, { hours = (hours + 1).coerceAtMost(72) })
            }
            SwitchRow(stringResource(R.string.sub_edit_auto_ping), stringResource(R.string.sub_edit_auto_ping_summary), autoPing) { autoPing = it }
            if (autoPing) {
                Stepper(stringResource(R.string.sub_edit_every_minutes), stringResource(R.string.sub_edit_minutes_value, minutes), { minutes = (minutes - 1).coerceAtLeast(1) }, { minutes = (minutes + 1).coerceAtMost(120) })
                Text(stringResource(R.string.sub_edit_auto_ping_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(android.R.string.cancel)) }
                Button(
                    onClick = {
                        onSave(
                            MainAction.SaveSubscription(
                                groupId = group.id,
                                name = name,
                                url = url,
                                autoUpdate = autoUpdate,
                                updateIntervalMinutes = hours * 60L,
                                autoPingMinutes = if (autoPing) minutes else 0,
                            )
                        )
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.sub_edit_save)) }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 4.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        IconButton(onClick = onMinus) { Icon(painterResource(R.drawable.ic_remove_24dp), contentDescription = stringResource(R.string.speedtest_decrease)) }
        Text(value, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(72.dp))
        IconButton(onClick = onPlus) { Icon(painterResource(R.drawable.ic_add_24dp), contentDescription = stringResource(R.string.speedtest_increase)) }
    }
}
