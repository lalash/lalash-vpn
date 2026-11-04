package com.v2ray.ang.ui.speedtest

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.launch

/** Config speed test: ping all selected configs, then upload/download for the best N. */
class SpeedTestActivity : BaseComponentActivity() {

    private val viewModel: SpeedTestViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            intent.getStringExtra(EXTRA_GROUP_ID)?.let(viewModel::preselectSource)
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.messages.collect { message ->
                    when (message) {
                        is SpeedTestMessage.Success -> toastSuccess(
                            if (message.count != null) getString(message.resId, message.count) else getString(message.resId)
                        )

                        is SpeedTestMessage.Failure -> toastError(message.resId)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadSources()
    }

    @Composable
    override fun ScreenContent() {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        SpeedTestScreen(
            state = state,
            onAction = viewModel::onAction,
            onOpenSettings = { startActivity(Intent(this, SpeedTestSettingsActivity::class.java)) },
            readClipboard = {
                try {
                    Utils.getClipboard(this)
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "SpeedTest: failed to read clipboard", e)
                    ""
                }
            },
            onBack = { finish() },
        )
    }

    companion object {
        /** Opens the screen with only this group selected for testing. */
        const val EXTRA_GROUP_ID = "group_id"
    }
}
