package com.v2ray.ang.core

import android.app.Service
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.enums.NotificationChannelType
import com.v2ray.ang.handler.KillSwitchPolicy
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.WarpSettings
import com.v2ray.ang.helper.NotificationHelper
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Kill switch: while connected, checks the connection every [KillSwitchPolicy.CHECK_INTERVAL_MS].
 * When it is lost the core is reloaded in place, with the VPN interface kept open, so apps stay
 * blocked instead of reaching the internet without the VPN; if that does not help, the next best
 * config of the same group is used (when allowed). A notification tells the user meanwhile.
 *
 * Owned by [CoreServiceManager]: started after a successful start, stopped in its stop path. The
 * job is a child of [scope], which lives as long as the daemon process.
 */
internal class ConnectionWatchdog(
    private val isConnected: () -> Boolean,
    private val isBusy: () -> Boolean,
    private val probe: () -> Boolean,
    private val reload: () -> Boolean,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    @Synchronized
    fun start(service: Service) {
        stop(service)
        job = scope.launch {
            reportAndroidKillSwitch(service)
            if (!MmkvManager.decodeSettingsBool(AppConfig.PREF_KILL_SWITCH, false)) return@launch
            var state = KillSwitchPolicy.State()
            while (isActive) {
                delay(KillSwitchPolicy.CHECK_INTERVAL_MS)
                if (!isConnected() || isBusy()) continue
                // Without any upstream network the config is not at fault; do not count it.
                if (!hasUpstreamNetwork(service)) continue
                val ok = try {
                    probe()
                } catch (e: Exception) {
                    false
                }
                val autoSwitch = MmkvManager.decodeSettingsBool(AppConfig.PREF_KILL_SWITCH_AUTO_SWITCH, true)
                val (next, action) = KillSwitchPolicy.next(state, ok, autoSwitch)
                state = next
                when (action) {
                    KillSwitchPolicy.Action.NONE -> Unit
                    KillSwitchPolicy.Action.RECOVERED -> NotificationHelper.cancel(NotificationChannelType.CONNECTION_WATCH, service)
                    KillSwitchPolicy.Action.RELOAD -> {
                        notifyLost(service, null)
                        LogUtil.w(AppConfig.TAG, "KillSwitch: connection lost, reloading core")
                        reload()
                    }
                    KillSwitchPolicy.Action.SWITCH_CONFIG -> switchConfig(service)
                }
            }
        }
    }

    @Synchronized
    fun stop(service: Service?) {
        job?.cancel()
        job = null
        service?.let { NotificationHelper.cancel(NotificationChannelType.CONNECTION_WATCH, it) }
    }

    private fun switchConfig(service: Service) {
        val current = MmkvManager.getSelectServer() ?: return
        val subId = MmkvManager.decodeServerConfig(current)?.subscriptionId ?: return
        val candidates = MmkvManager.decodeServerList(subId)
            .filter { WarpSettings.modeFor(it) == null }
            .map { it to (MmkvManager.decodeServerAffiliationInfo(it)?.testDelayMillis ?: 0L) }
        val next = KillSwitchPolicy.pickNextServer(current, candidates)
        if (next == null) {
            LogUtil.w(AppConfig.TAG, "KillSwitch: no other config in group $subId, reloading")
            reload()
            return
        }
        LogUtil.w(AppConfig.TAG, "KillSwitch: switching from $current to $next")
        MmkvManager.setSelectServer(next)
        notifyLost(service, MmkvManager.decodeServerConfig(next)?.remarks)
        reload()
    }

    private fun notifyLost(service: Service, switchedTo: String?) {
        val content = if (switchedTo == null) {
            service.getString(R.string.killswitch_notify_reconnecting)
        } else {
            service.getString(R.string.killswitch_notify_switched, switchedTo)
        }
        NotificationHelper.notify(
            NotificationChannelType.CONNECTION_WATCH,
            service,
            service.getString(R.string.killswitch_notify_title),
            content,
        )
    }

    private fun hasUpstreamNetwork(service: Service): Boolean {
        val cm = service.getSystemService(ConnectivityManager::class.java) ?: return true
        // This app is excluded from its own VPN, so its default network is the real one.
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Stores whether Android's own always-on VPN and "block connections without VPN" are on for
     * this app, so the kill switch page can show it. Only a running VpnService can ask, and only
     * from Android 10 (API 29).
     */
    private fun reportAndroidKillSwitch(service: Service) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val vpn = service as? VpnService ?: return
        // Stored as "1"/"0" so the page can tell "off" from "not known yet".
        MmkvManager.encodeSettings(AppConfig.PREF_VPN_ALWAYS_ON, if (vpn.isAlwaysOn) "1" else "0")
        MmkvManager.encodeSettings(AppConfig.PREF_VPN_LOCKDOWN, if (vpn.isLockdownEnabled) "1" else "0")
    }
}
