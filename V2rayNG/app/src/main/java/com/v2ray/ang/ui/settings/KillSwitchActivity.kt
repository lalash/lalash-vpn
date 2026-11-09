package com.v2ray.ang.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.MmkvManager.rememberMmkvBool
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.SettingsSwitchItem
import com.v2ray.ang.ui.compose.colorConfigType
import com.v2ray.ang.ui.compose.colorConnect
import com.v2ray.ang.ui.compose.colorOnConnect
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Kill switch settings: the app's own watchdog (block and reconnect while it runs) and the status
 * of Android's always-on VPN / "block connections without VPN", with a shortcut to turn them on.
 */
class KillSwitchActivity : BaseComponentActivity() {

    @Composable
    override fun ScreenContent() {
        var enabled by rememberMmkvBool(AppConfig.PREF_KILL_SWITCH, false)
        var autoSwitch by rememberMmkvBool(AppConfig.PREF_KILL_SWITCH_AUTO_SWITCH, true)
        var alwaysOn by remember { mutableStateOf<String?>(null) }
        var lockdown by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                alwaysOn = MmkvManager.decodeSettingsString(AppConfig.PREF_VPN_ALWAYS_ON)
                lockdown = MmkvManager.decodeSettingsString(AppConfig.PREF_VPN_LOCKDOWN)
            }
        }
        Scaffold(
            topBar = { AppTopBar(title = stringResource(R.string.killswitch_title), onBackClick = { finish() }) }
        ) { innerPadding ->
            Column(
                modifier = Modifier
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
                    Text(stringResource(R.string.killswitch_intro), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                }
                SettingsSwitchItem(
                    title = stringResource(R.string.killswitch_enable),
                    summary = stringResource(R.string.killswitch_enable_summary),
                    checked = enabled,
                    onCheckedChange = { enabled = it }
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.killswitch_auto_switch),
                    summary = stringResource(R.string.killswitch_auto_switch_summary),
                    checked = autoSwitch,
                    onCheckedChange = { autoSwitch = it },
                    enabled = enabled
                )
                Text(
                    stringResource(R.string.killswitch_apply_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Text(
                    stringResource(R.string.killswitch_android_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colorConfigType,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp)
                )
                Text(
                    stringResource(R.string.killswitch_android_body),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                AndroidStateRow(R.string.killswitch_always_on, alwaysOn)
                AndroidStateRow(R.string.killswitch_lockdown, lockdown)
                Button(
                    onClick = ::openAndroidVpnSettings,
                    colors = ButtonDefaults.buttonColors(containerColor = colorConnect, contentColor = colorOnConnect),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) { Text(stringResource(R.string.killswitch_open_android), fontWeight = FontWeight.Bold) }
            }
        }
    }

    /** One Android setting with its last reported state ("1", "0" or not known yet). */
    @Composable
    private fun AndroidStateRow(labelRes: Int, state: String?) {
        val (text, color) = when (state) {
            "1" -> stringResource(R.string.killswitch_state_on) to Color(0xFF15803D)
            "0" -> stringResource(R.string.killswitch_state_off) to Color(0xFFB91C1C)
            else -> stringResource(R.string.killswitch_state_unknown) to MaterialTheme.colorScheme.onSurfaceVariant
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {}
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(text, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.SemiBold)
        }
    }

    private fun openAndroidVpnSettings() {
        try {
            startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            LogUtil.e(AppConfig.TAG, "Cannot open system VPN settings", e)
            toastError(R.string.toast_system_vpn_settings_unavailable)
        } catch (e: SecurityException) {
            LogUtil.e(AppConfig.TAG, "Cannot open system VPN settings", e)
            toastError(R.string.toast_system_vpn_settings_unavailable)
        }
    }
}
