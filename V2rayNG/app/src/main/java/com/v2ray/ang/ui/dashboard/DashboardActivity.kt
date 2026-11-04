package com.v2ray.ang.ui.dashboard

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.ui.warp.ChainSettingsActivity
import kotlinx.coroutines.launch

/**
 * Connection dashboard: big connect button, current config with a quick ping test,
 * exit country, live traffic chart, and the "Iran route" switch.
 */
class DashboardActivity : HelperBaseComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels()

    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) startCore()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        DashboardEffect.RestartService -> LauncherManager.restartService(this@DashboardActivity)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshConfig()
        // Back from the exit chain page: apply a changed chain to the running connection.
        if (SettingsChangeManager.consumeRestartService() && viewModel.uiState.value.isRunning) {
            LauncherManager.restartService(this)
        }
    }

    @Composable
    override fun ScreenContent() {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        DashboardScreen(
            state = state,
            onAction = { action ->
                when (action) {
                    DashboardAction.ToggleService -> toggleService(state.isRunning)
                    // The config list is the main screen this one was opened from.
                    DashboardAction.OpenConfigList -> finish()
                    DashboardAction.OpenExitChain -> startActivity(Intent(this, ChainSettingsActivity::class.java))
                    else -> viewModel.onAction(action)
                }
            },
            onBack = { finish() },
        )
    }

    private fun toggleService(isRunning: Boolean) {
        if (isRunning) {
            LauncherManager.stopService(this)
            return
        }
        if (!SettingsManager.isVpnMode()) {
            startCore()
            return
        }
        val intent = VpnService.prepare(this)
        if (intent == null) startCore() else requestVpnPermission.launch(intent)
    }

    private fun startCore() {
        if (viewModel.uiState.value.configGuid.isNullOrEmpty()) {
            toast(R.string.title_file_chooser)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            checkAndRequestPermission(PermissionType.ACCESS_LOCAL_NETWORK) {}
        }
        LauncherManager.startService(this)
    }
}
