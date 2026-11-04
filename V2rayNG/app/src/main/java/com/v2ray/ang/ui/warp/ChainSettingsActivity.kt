package com.v2ray.ang.ui.warp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager.rememberMmkvString
import com.v2ray.ang.handler.WarpSettings
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.colorConfigType
import com.v2ray.ang.ui.compose.colorFabActive
import com.v2ray.ang.ui.dashboard.flagEmoji

/**
 * Exit chain: the config selected in the list connects as usual, then traffic leaves through
 * WARP or a Psiphon server in the chosen country. Changes restart a running connection when
 * the user goes back (the setting change flags a restart).
 */
class ChainSettingsActivity : BaseComponentActivity() {

    @Composable
    override fun ScreenContent() {
        var exit by rememberMmkvString(AppConfig.PREF_ENGINE_CHAIN_EXIT, WarpSettings.CHAIN_OFF)
        var region by rememberMmkvString(AppConfig.PREF_ENGINE_PSIPHON_REGION, "")
        Scaffold(
            topBar = { AppTopBar(title = stringResource(R.string.chain_settings_title), onBackClick = { finish() }) }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.chain_intro), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                    }
                }
                item { ExitOption(WarpSettings.CHAIN_OFF, R.string.chain_off, R.string.chain_off_desc, exit) { exit = it } }
                item { ExitOption(WarpSettings.CHAIN_WARP, R.string.chain_warp, R.string.chain_warp_desc, exit) { exit = it } }
                item { ExitOption(WarpSettings.CHAIN_PSIPHON, R.string.chain_psiphon, R.string.chain_psiphon_desc, exit) { exit = it } }
                if (exit == WarpSettings.CHAIN_PSIPHON) {
                    item {
                        Text(
                            stringResource(R.string.chain_psiphon_servers),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colorConfigType,
                            modifier = Modifier.padding(top = 12.dp, start = 4.dp)
                        )
                    }
                    items(WarpSettings.PSIPHON_REGIONS, key = { it.ifEmpty { "auto" } }) { code ->
                        RegionRow(code, selected = code == region) { region = code }
                    }
                }
                item {
                    Text(
                        stringResource(R.string.chain_apply_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp)
                    )
                }
            }
        }
    }

    @Composable
    private fun ExitOption(value: String, titleRes: Int, descRes: Int, current: String, onSelect: (String) -> Unit) {
        val selected = value == current
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (selected) colorFabActive.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) colorFabActive else MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton) { onSelect(value) }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp)) {
                RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = colorFabActive), modifier = Modifier.padding(horizontal = 10.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(descRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    /** One Psiphon exit country: flag, localized name and code; "" is automatic. */
    @Composable
    private fun RegionRow(code: String, selected: Boolean, onSelect: () -> Unit) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (selected) colorFabActive.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
            border = if (selected) BorderStroke(1.dp, colorFabActive) else null,
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(if (code.isEmpty()) "🌐" else flagEmoji(code), fontSize = 22.sp)
                Text(
                    if (code.isEmpty()) stringResource(R.string.engine_psiphon_region_auto) else countryName(code),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
                if (code.isNotEmpty()) {
                    Text(code, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = colorFabActive))
            }
        }
    }
}
