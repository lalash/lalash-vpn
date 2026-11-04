package com.v2ray.ang.service

import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.dto.SpeedTestRequest
import com.v2ray.ang.dto.SpeedTestResult
import com.v2ray.ang.dto.SpeedTestState
import com.v2ray.ang.enums.NotificationChannelType
import com.v2ray.ang.extension.serializable
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.handler.ConfigSpeedTester
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SpeedTestSettings
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.helper.NotificationHelper
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Runs one config speed test at a time in the `:tasks` process and streams
 * [com.v2ray.ang.dto.SpeedTestUpdate]s to the UI. A new start cancels the previous run.
 */
class SpeedTestService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var runJob: Job? = null
    private var lastNotified = 0L

    private val cancelAction by lazy {
        val intent = Intent(this, SpeedTestService::class.java).putExtra(EXTRA_CANCEL, true)
        val pendingIntent = PendingIntent.getService(
            this,
            NOTIFICATION_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        NotificationCompat.Action.Builder(
            R.drawable.ic_stop_24dp,
            getString(R.string.action_cancel),
            pendingIntent
        ).build()
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let(AppLocaleManager::localizedContext))
    }

    override fun onCreate() {
        super.onCreate()
        CoreNativeManager.initCoreEnv(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NotificationHelper.startForeground(
            this,
            NotificationChannelType.CORE_TEST,
            getString(R.string.app_name),
            getString(R.string.speedtest_title),
            cancelAction
        )
        if (intent?.getBooleanExtra(EXTRA_CANCEL, false) == true) {
            cancelRun(intent.getStringExtra(MessageHelper.EXTRA_REQUEST_ID))
            finish()
            return START_NOT_STICKY
        }
        val request = intent?.serializable<SpeedTestRequest>(EXTRA_REQUEST)
        val requestId = intent?.getStringExtra(MessageHelper.EXTRA_REQUEST_ID).orEmpty()
        if (request == null || request.guids.isEmpty()) {
            MessageHelper.sendMsg2UI(this, AppConfig.MSG_SPEEDTEST_FINISH, "", requestId)
            finish()
            return START_NOT_STICKY
        }

        runJob?.cancel()
        // Lazy start so runJob is assigned before the body (and its finally) can run.
        runJob = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val tester = ConfigSpeedTester(this@SpeedTestService, SpeedTestSettings.load()) { update ->
                    update.result?.let(::persistResult)
                    MessageHelper.sendMsg2UI(this@SpeedTestService, AppConfig.MSG_SPEEDTEST_UPDATE, update, requestId)
                    notifyProgress(update.pingDone, update.pingTotal, update.speedDone, update.speedTotal)
                }
                tester.run(request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "SpeedTest: run failed (${request.guids.size} configs)", e)
            } finally {
                MessageHelper.sendMsg2UI(this@SpeedTestService, AppConfig.MSG_SPEEDTEST_FINISH, "", requestId)
                // A newer run may have replaced this one; only the current run stops the service.
                if (runJob === coroutineContext[Job]) {
                    runJob = null
                    finish()
                }
            }
        }
        runJob?.start()
        return START_NOT_STICKY
    }

    /** Keeps final results with the config so the config list can show ping and last speeds. */
    private fun persistResult(result: SpeedTestResult) {
        when (result.state) {
            SpeedTestState.DONE, SpeedTestState.QUEUED, SpeedTestState.SKIPPED, SpeedTestState.DEAD ->
                MmkvManager.encodeServerSpeedResult(
                    result.guid, result.pingMs, result.jitterMs, result.downloadMbps, result.uploadMbps
                )

            SpeedTestState.PINGING, SpeedTestState.TESTING -> Unit
        }
    }

    private fun notifyProgress(pingDone: Int, pingTotal: Int, speedDone: Int, speedTotal: Int) {
        val now = System.currentTimeMillis()
        if (now - lastNotified < 1000) return
        lastNotified = now
        NotificationHelper.updateNotification(
            channelType = NotificationChannelType.CORE_TEST,
            context = this,
            title = getString(R.string.speedtest_title),
            content = getString(R.string.speedtest_notification_progress, pingDone, pingTotal, speedDone, speedTotal)
        )
    }

    private fun cancelRun(requestId: String?) {
        runJob?.cancel()
        runJob = null
        MessageHelper.sendMsg2UI(this, AppConfig.MSG_SPEEDTEST_FINISH, "", requestId)
    }

    private fun finish() {
        NotificationHelper.stopForeground(this)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        NotificationHelper.stopForeground(this)
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_REQUEST = "content"
        private const val EXTRA_CANCEL = "cancel"
        private const val NOTIFICATION_REQUEST_CODE = 21

        /** Starts (or restarts) a speed test run. Results arrive as MSG_SPEEDTEST_UPDATE broadcasts. */
        fun start(context: Context, request: SpeedTestRequest, requestId: String) {
            val intent = Intent().apply {
                component = ComponentName(context, SpeedTestService::class.java)
                putExtra(EXTRA_REQUEST, request)
                putExtra(MessageHelper.EXTRA_REQUEST_ID, requestId)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "SpeedTest: failed to start service", e)
                MessageHelper.sendMsg2UI(context, AppConfig.MSG_SPEEDTEST_FINISH, "", requestId)
            }
        }

        /** Stops a running test; does not wake the service if it is not running. */
        fun cancel(context: Context) {
            context.stopService(Intent().apply { component = ComponentName(context, SpeedTestService::class.java) })
        }
    }
}
