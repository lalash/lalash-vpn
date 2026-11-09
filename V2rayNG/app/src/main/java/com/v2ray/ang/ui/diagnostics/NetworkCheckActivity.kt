package com.v2ray.ang.ui.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.ui.base.BaseComponentActivity

/** Checks the phone's own internet and says whether the problem is the network or the config. */
class NetworkCheckActivity : BaseComponentActivity() {

    private val viewModel: NetworkCheckViewModel by viewModels()

    @Composable
    override fun ScreenContent() {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        NetworkCheckScreen(
            state = state,
            onAction = viewModel::onAction,
            onCopy = ::copyReport,
            onBack = { finish() },
        )
    }

    private fun copyReport(report: String) {
        getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText(getString(R.string.netcheck_title), report))
        toastSuccess(R.string.netcheck_copied)
    }
}
