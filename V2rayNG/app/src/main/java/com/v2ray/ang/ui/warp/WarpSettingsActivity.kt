package com.v2ray.ang.ui.warp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager.rememberMmkvString
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.WarpSettings
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.SettingsListItem
import com.v2ray.ang.ui.compose.colorConfigType
import com.v2ray.ang.ui.compose.colorFabActive
import com.v2ray.ang.ui.compose.colorOnFabActive
import com.v2ray.ang.ui.dashboard.flagEmoji
import java.util.Locale
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * WARP (Cloudflare, through the Aether engine): add the WARP entry to the config list and
 * choose the engine options. Changes restart a running connection when the user returns.
 */
class WarpSettingsActivity : BaseComponentActivity() {

    @Composable
    override fun ScreenContent() {
        Scaffold(
            topBar = { AppTopBar(title = stringResource(R.string.warp_settings_title), onBackClick = { finish() }) }
        ) { innerPadding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(stringResource(R.string.warp_intro), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                }
                Button(
                    onClick = ::addWarp,
                    colors = ButtonDefaults.buttonColors(containerColor = colorFabActive, contentColor = colorOnFabActive),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) { Text(stringResource(R.string.warp_add_to_list), fontWeight = FontWeight.Bold) }

                SectionTitle(R.string.engine_section_psiphon)
                PsiphonRegionOption()
                SectionTitle(R.string.engine_section_warp)
                ListOption(
                    R.string.warp_pref_protocol, AppConfig.PREF_WARP_PROTOCOL, WarpSettings().protocol,
                    WarpSettings.PROTOCOLS,
                    listOf(R.string.warp_protocol_auto, R.string.warp_protocol_wireguard, R.string.warp_protocol_masque, R.string.warp_protocol_gool, R.string.warp_protocol_mim),
                )
                ListOption(
                    R.string.warp_pref_scan, AppConfig.PREF_WARP_SCAN, WarpSettings().scan,
                    WarpSettings.SCANS,
                    listOf(R.string.warp_scan_turbo, R.string.warp_scan_balanced, R.string.warp_scan_thorough, R.string.warp_scan_stealth, R.string.warp_scan_ironclad),
                )
                ListOption(
                    R.string.warp_pref_ip, AppConfig.PREF_WARP_IP, WarpSettings().ipVersion,
                    WarpSettings.IP_VERSIONS,
                    listOf(R.string.warp_ip_v4, R.string.warp_ip_v6, R.string.warp_ip_both),
                )
                ListOption(
                    R.string.warp_pref_noize, AppConfig.PREF_WARP_NOIZE, WarpSettings().noize,
                    WarpSettings.NOIZE_PROFILES,
                    listOf(R.string.warp_noize_off, R.string.warp_noize_light, R.string.warp_noize_firewall, R.string.warp_noize_balanced, R.string.warp_noize_gfw, R.string.warp_noize_aggressive),
                )
                Text(
                    stringResource(R.string.warp_credit),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }

    @Composable
    private fun SectionTitle(textRes: Int) {
        Text(
            stringResource(textRes),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = colorConfigType,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
        )
    }

    /** Psiphon exit country, shown with its flag and localized name. */
    @Composable
    private fun PsiphonRegionOption() {
        var value by rememberMmkvString(AppConfig.PREF_ENGINE_PSIPHON_REGION, "")
        val automatic = stringResource(R.string.engine_psiphon_region_auto)
        val locale = Locale.getDefault()
        SettingsListItem(
            title = stringResource(R.string.engine_psiphon_region),
            entries = WarpSettings.PSIPHON_REGIONS.map { code ->
                if (code.isEmpty()) automatic
                else "${flagEmoji(code)}  ${Locale.Builder().setRegion(code).build().getDisplayCountry(locale)}"
            },
            values = WarpSettings.PSIPHON_REGIONS,
            selectedValue = value,
            onSelected = { value = it }
        )
    }

    @Composable
    private fun ListOption(titleRes: Int, key: String, default: String, values: List<String>, labels: List<Int>) {
        var value by rememberMmkvString(key, default)
        SettingsListItem(
            title = stringResource(titleRes),
            entries = labels.map { stringResource(it) },
            values = values,
            selectedValue = value,
            onSelected = { value = it }
        )
    }

    private fun addWarp() {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    WarpSettings.ensureEngineEntries()
                    true
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to add WARP entry", e)
                    false
                }
            }
            if (ok) {
                // The main screen reloads its groups and restarts a running connection on return.
                SettingsChangeManager.makeSetupGroupTab()
                SettingsChangeManager.makeRestartService()
                toastSuccess(R.string.warp_added)
                finish()
            } else {
                toastError(R.string.toast_failure)
            }
        }
    }
}
