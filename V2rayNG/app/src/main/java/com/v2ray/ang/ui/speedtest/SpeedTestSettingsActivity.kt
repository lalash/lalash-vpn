package com.v2ray.ang.ui.speedtest

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.net.Uri
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager.rememberMmkvBool
import com.v2ray.ang.handler.MmkvManager.rememberMmkvString
import com.v2ray.ang.handler.SpeedTestSettings
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.SettingsEditItem
import com.v2ray.ang.ui.compose.SettingsListItem
import com.v2ray.ang.ui.compose.SettingsSwitchItem

/**
 * Speed test settings in three tabs, like the desktop tester: measurement, pacing, test server.
 * Values are edited the same way as the app's main Settings screen (MMKV-backed state).
 */
class SpeedTestSettingsActivity : BaseComponentActivity() {

    @Composable
    override fun ScreenContent() {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var resetCount by rememberSaveable { mutableIntStateOf(0) }
        Scaffold(
            topBar = { AppTopBar(title = stringResource(R.string.speedtest_settings_title), onBackClick = { finish() }) },
            bottomBar = {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            SpeedTestSettings.resetToDefaults()
                            resetCount++
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.speedtest_reset_defaults)) }
                }
            }
        ) { innerPadding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
            ) {
                val tabs = listOf(R.string.speedtest_tab_measure, R.string.speedtest_tab_pacing, R.string.speedtest_tab_server)
                PrimaryTabRow(selectedTabIndex = tab) {
                    tabs.forEachIndexed { index, res ->
                        Tab(selected = tab == index, onClick = { tab = index }, text = { Text(stringResource(res)) })
                    }
                }
                // Re-read every value after a reset.
                key(resetCount) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        when (tab) {
                            0 -> MeasureTab()
                            1 -> PacingTab()
                            else -> ServerTab()
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun MeasureTab() {
        val d = SpeedTestSettings()
        NumberItem(R.string.speedtest_pref_duration_download, AppConfig.PREF_ST_DURATION_DOWNLOAD, d.durationDownloadSec.toString())
        NumberItem(R.string.speedtest_pref_duration_upload, AppConfig.PREF_ST_DURATION_UPLOAD, d.durationUploadSec.toString())
        NumberItem(R.string.speedtest_pref_warmup_download, AppConfig.PREF_ST_WARMUP_DOWNLOAD, d.warmupDownloadSec.toString())
        NumberItem(R.string.speedtest_pref_warmup_upload, AppConfig.PREF_ST_WARMUP_UPLOAD, d.warmupUploadSec.toString())
        NumberItem(R.string.speedtest_pref_streams_download, AppConfig.PREF_ST_STREAMS_DOWNLOAD, d.streamsDownload.toString())
        NumberItem(R.string.speedtest_pref_streams_upload, AppConfig.PREF_ST_STREAMS_UPLOAD, d.streamsUpload.toString())
        NumberItem(R.string.speedtest_pref_ping_probes, AppConfig.PREF_ST_PING_PROBES, d.pingProbes.toString())
        Note(R.string.speedtest_note_measure)
    }

    @Composable
    private fun PacingTab() {
        val d = SpeedTestSettings()
        NumberItem(R.string.speedtest_pref_ping_concurrency, AppConfig.PREF_ST_PING_CONCURRENCY, d.pingConcurrency.toString())
        NumberItem(R.string.speedtest_pref_max_ping, AppConfig.PREF_ST_MAX_PING, d.maxPingMs.toString())
        NumberItem(R.string.speedtest_pref_request_timeout, AppConfig.PREF_ST_REQUEST_TIMEOUT, d.requestTimeoutSec.toString())
        SwitchItem(R.string.speedtest_pref_skip_dead, R.string.speedtest_pref_skip_dead_summary, AppConfig.PREF_ST_SKIP_DEAD, d.skipDead)
        SwitchItem(R.string.speedtest_pref_fetch_exit_ip, null, AppConfig.PREF_ST_FETCH_EXIT_IP, d.fetchExitIp)
        SwitchItem(R.string.speedtest_pref_verify_tls, null, AppConfig.PREF_ST_VERIFY_TLS, d.verifyTls)
        Note(R.string.speedtest_note_pacing)
    }

    @Composable
    private fun ServerTab() {
        val d = SpeedTestSettings()
        DownloadServersItem()
        TextItem(R.string.speedtest_pref_down_url, AppConfig.PREF_ST_DOWN_URL, "")
        TextItem(R.string.speedtest_pref_up_url, AppConfig.PREF_ST_UP_URL, d.upUrl)
        PingUrlItem(d.pingUrl)
        NumberItem(R.string.speedtest_pref_download_size, AppConfig.PREF_ST_DOWNLOAD_SIZE_MB, d.downloadSizeMb.toString())
        Note(R.string.speedtest_note_server)
    }

    /**
     * Built-in download servers, each with its own switch. They are tried in this order and the
     * next one is used when a server does not answer; switched-off keys are stored.
     */
    @Composable
    private fun DownloadServersItem() {
        var offKeys by rememberMmkvString(AppConfig.PREF_ST_DOWN_SERVERS_OFF, "")
        val off = offKeys.split(',').filter { it.isNotEmpty() }.toSet()
        Text(
            stringResource(R.string.speedtest_pref_down_servers),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
        )
        Text(
            stringResource(R.string.speedtest_pref_down_servers_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        SpeedTestSettings.DOWNLOAD_SERVERS.forEach { server ->
            SettingsSwitchItem(
                title = server.label,
                summary = Uri.parse(server.url).host,
                checked = server.key !in off,
                onCheckedChange = { on ->
                    offKeys = (if (on) off - server.key else off + server.key).joinToString(",")
                }
            )
        }
    }

    @Composable
    private fun NumberItem(titleRes: Int, key: String, default: String) {
        var value by rememberMmkvString(key, default)
        SettingsEditItem(title = stringResource(titleRes), value = value, onValueChanged = { value = it }, keyboardNumber = true)
    }

    /** Ping and jitter target: one of several well-known endpoints, or the app's own test URL. */
    @Composable
    private fun PingUrlItem(default: String) {
        var value by rememberMmkvString(AppConfig.PREF_ST_PING_URL, default)
        val entries = SpeedTestSettings.PING_URL_PRESETS.map { "${it.first} — ${it.second}" } +
            stringResource(R.string.speedtest_ping_url_same_as_app)
        val values = SpeedTestSettings.PING_URL_PRESETS.map { it.second } + SpeedTestSettings.PING_URL_SAME_AS_APP
        SettingsListItem(
            title = stringResource(R.string.speedtest_pref_ping_url),
            entries = entries,
            values = values,
            selectedValue = value,
            onSelected = { value = it }
        )
    }

    @Composable
    private fun TextItem(titleRes: Int, key: String, default: String) {
        var value by rememberMmkvString(key, default)
        SettingsEditItem(title = stringResource(titleRes), value = value, onValueChanged = { value = it })
    }

    @Composable
    private fun SwitchItem(titleRes: Int, summaryRes: Int?, key: String, default: Boolean) {
        var checked by rememberMmkvBool(key, default)
        SettingsSwitchItem(
            title = stringResource(titleRes),
            summary = summaryRes?.let { stringResource(it) },
            checked = checked,
            onCheckedChange = { checked = it }
        )
    }

    @Composable
    private fun Note(textRes: Int) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(stringResource(textRes), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
        }
    }
}
