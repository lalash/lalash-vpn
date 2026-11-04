package com.v2ray.ang.ui.usage

import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.ui.base.BaseComponentActivity

/** Total and per-config proxy traffic, recorded by the daemon's stats loop. */
class UsageActivity : BaseComponentActivity() {

    private val viewModel: UsageViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        viewModel.reload()
    }

    @Composable
    override fun ScreenContent() {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        UsageScreen(state = state, onAction = viewModel::onAction, onBack = { finish() })
    }
}
