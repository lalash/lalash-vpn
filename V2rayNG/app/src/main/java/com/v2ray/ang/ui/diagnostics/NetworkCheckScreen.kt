package com.v2ray.ang.ui.diagnostics

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.dto.CheckGroup
import com.v2ray.ang.dto.CheckItem
import com.v2ray.ang.dto.CheckStatus
import com.v2ray.ang.dto.FailKind
import com.v2ray.ang.dto.NetworkConclusion
import com.v2ray.ang.dto.NetworkInfo
import com.v2ray.ang.dto.NetworkVerdict
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.colorFabActive
import com.v2ray.ang.ui.compose.colorOnFabActive

private val okColor = Color(0xFF15803D)
private val warnColor = Color(0xFFA16207)
private val failColor = Color(0xFFB91C1C)

@Composable
fun NetworkCheckScreen(
    state: NetworkCheckUiState,
    onAction: (NetworkCheckAction) -> Unit,
    onCopy: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        topBar = { AppTopBar(title = stringResource(R.string.netcheck_title), onBackClick = onBack) },
        bottomBar = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Button(
                    onClick = { onAction(NetworkCheckAction.Run) },
                    enabled = !state.running,
                    colors = ButtonDefaults.buttonColors(containerColor = colorFabActive, contentColor = colorOnFabActive),
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.netcheck_run_again), fontWeight = FontWeight.Bold) }
                OutlinedButton(
                    onClick = { onCopy(buildReport(context, state)) },
                    enabled = !state.running,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.netcheck_copy)) }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                if (state.running) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.netcheck_running), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = colorFabActive)
                        Text(stringResource(R.string.netcheck_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    state.conclusion?.let { ConclusionCard(it) }
                }
            }
            CheckGroup.entries.forEach { group ->
                val rows = state.items.filter { it.group == group }
                if (rows.isEmpty()) return@forEach
                item(key = "header_${group.name}") { GroupHeader(group, rows) }
                items(rows, key = { it.id }) { CheckRow(it, state.info) }
            }
        }
    }
}

@Composable
private fun ConclusionCard(conclusion: NetworkConclusion) {
    val (title, body) = verdictText(conclusion.verdict)
    val color = when (conclusion.verdict) {
        NetworkVerdict.ALL_GOOD, NetworkVerdict.FILTERED_NORMAL -> okColor
        NetworkVerdict.INTERNATIONAL_CUT, NetworkVerdict.PHONE_DNS_BROKEN -> warnColor
        else -> failColor
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
            flags(conclusion).forEach { flag ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("•", color = warnColor, fontWeight = FontWeight.Bold)
                    Text(stringResource(flag), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(group: CheckGroup, rows: List<CheckItem>) {
    val done = rows.filter { it.status != CheckStatus.PENDING && it.status != CheckStatus.SKIPPED }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 2.dp)
    ) {
        Text(
            stringResource(groupTitle(group)),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (group != CheckGroup.NETWORK && done.isNotEmpty()) {
            Text(
                stringResource(R.string.netcheck_count, done.count { it.status == CheckStatus.OK }, rows.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CheckRow(item: CheckItem, info: NetworkInfo?) {
    val label = rowLabel(item)
    val detail = rowDetail(item, info)
    val value = rowValue(item)
    val reason = item.fail?.let { stringResource(failText(it)) }
    val spoken = listOfNotNull(label, detail, value, reason).joinToString(", ")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(vertical = 5.dp)
    ) {
        StatusIcon(item.status)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (detail.isNotEmpty()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (reason != null && item.status != CheckStatus.OK) {
                Text(reason, style = MaterialTheme.typography.labelSmall, color = if (item.status == CheckStatus.FAIL) failColor else warnColor)
            }
        }
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.labelMedium,
                color = statusColor(item.status),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun StatusIcon(status: CheckStatus) {
    Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
        if (status == CheckStatus.PENDING) {
            CircularProgressIndicator(strokeWidth = 2.dp, color = colorFabActive, modifier = Modifier.size(16.dp))
            return@Box
        }
        val (symbol, color) = when (status) {
            CheckStatus.OK -> "✓" to okColor
            CheckStatus.WARN -> "!" to warnColor
            CheckStatus.FAIL -> "✕" to failColor
            else -> "–" to MaterialTheme.colorScheme.onSurfaceVariant
        }
        Box(
            Modifier
                .size(22.dp)
                .background(color.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Text(symbol, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun statusColor(status: CheckStatus) = when (status) {
    CheckStatus.OK -> okColor
    CheckStatus.WARN -> warnColor
    CheckStatus.FAIL -> failColor
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun rowLabel(item: CheckItem): String = when (item.id) {
    "network" -> stringResource(R.string.netcheck_row_network)
    "gateway" -> stringResource(R.string.netcheck_row_gateway)
    "system_dns" -> stringResource(R.string.netcheck_row_system_dns)
    else -> item.label
}

@Composable
private fun rowDetail(item: CheckItem, info: NetworkInfo?): String {
    if (item.id != "network") return item.detail
    info ?: return ""
    val transport = stringResource(transportText(info.transport))
    val extras = listOfNotNull(
        if (info.connected && !info.validated) stringResource(R.string.netcheck_not_validated) else null,
        if (info.vpnActive) stringResource(R.string.netcheck_vpn_on) else null,
    )
    return (listOf(transport) + extras).joinToString(" · ")
}

/** "ping 12 ms", "answers in 30 ms" … for the row's right side, or "×" when nothing answered. */
@Composable
private fun rowValue(item: CheckItem): String? {
    if (item.status == CheckStatus.PENDING || item.status == CheckStatus.SKIPPED || item.id == "network") return null
    val parts = buildList {
        item.pingMs?.let { add(stringResource(if (item.pingByTcp) R.string.netcheck_tcp_ms else R.string.netcheck_ping_ms, it.toInt())) }
        item.timeMs?.let {
            val res = if (item.group == CheckGroup.IRAN_DNS || item.group == CheckGroup.FOREIGN_DNS || item.id == "system_dns") {
                R.string.netcheck_answer_ms
            } else {
                R.string.netcheck_open_ms
            }
            add(stringResource(res, it.toInt()))
        }
    }
    return if (parts.isEmpty()) "×" else parts.joinToString("\n")
}

private fun groupTitle(group: CheckGroup) = when (group) {
    CheckGroup.NETWORK -> R.string.netcheck_group_network
    CheckGroup.IRAN_SITES -> R.string.netcheck_group_iran_sites
    CheckGroup.IRAN_DNS -> R.string.netcheck_group_iran_dns
    CheckGroup.FOREIGN_SITES -> R.string.netcheck_group_foreign_sites
    CheckGroup.FOREIGN_DNS -> R.string.netcheck_group_foreign_dns
}

private fun transportText(transport: NetworkInfo.Transport) = when (transport) {
    NetworkInfo.Transport.WIFI -> R.string.netcheck_transport_wifi
    NetworkInfo.Transport.CELLULAR -> R.string.netcheck_transport_cellular
    NetworkInfo.Transport.ETHERNET -> R.string.netcheck_transport_ethernet
    NetworkInfo.Transport.OTHER -> R.string.netcheck_transport_other
    NetworkInfo.Transport.NONE -> R.string.netcheck_transport_none
}

private fun failText(kind: FailKind) = when (kind) {
    FailKind.NO_PING -> R.string.netcheck_fail_no_ping
    FailKind.DNS_FAILED -> R.string.netcheck_fail_dns_failed
    FailKind.DNS_POISONED -> R.string.netcheck_fail_dns_poisoned
    FailKind.CONNECT_TIMEOUT -> R.string.netcheck_fail_connect_timeout
    FailKind.CONNECT_REFUSED -> R.string.netcheck_fail_connect_refused
    FailKind.TLS_BLOCKED -> R.string.netcheck_fail_tls_blocked
    FailKind.NO_DNS_ANSWER -> R.string.netcheck_fail_no_dns_answer
    FailKind.HTTP_ERROR -> R.string.netcheck_fail_http_error
}

private fun verdictText(verdict: NetworkVerdict) = when (verdict) {
    NetworkVerdict.NO_NETWORK -> R.string.netcheck_verdict_no_network_title to R.string.netcheck_verdict_no_network_body
    NetworkVerdict.ROUTER_PROBLEM -> R.string.netcheck_verdict_router_title to R.string.netcheck_verdict_router_body
    NetworkVerdict.NO_INTERNET -> R.string.netcheck_verdict_no_internet_title to R.string.netcheck_verdict_no_internet_body
    NetworkVerdict.PHONE_DNS_BROKEN -> R.string.netcheck_verdict_dns_title to R.string.netcheck_verdict_dns_body
    NetworkVerdict.INTERNATIONAL_CUT -> R.string.netcheck_verdict_cut_title to R.string.netcheck_verdict_cut_body
    NetworkVerdict.FILTERED_NORMAL -> R.string.netcheck_verdict_filtered_title to R.string.netcheck_verdict_filtered_body
    NetworkVerdict.ALL_GOOD -> R.string.netcheck_verdict_good_title to R.string.netcheck_verdict_good_body
}

private fun flags(c: NetworkConclusion): List<Int> = listOfNotNull(
    if (c.dnsPoisoned) R.string.netcheck_flag_dns_poisoned else null,
    if (c.tlsFiltering) R.string.netcheck_flag_tls else null,
    if (c.udpAbroadBlocked) R.string.netcheck_flag_udp else null,
    if (c.packetsWithoutReply) R.string.netcheck_flag_no_reply else null,
)

/** Plain-text report for support chats, in the app's language. */
private fun buildReport(context: Context, state: NetworkCheckUiState): String = buildString {
    appendLine(context.getString(R.string.netcheck_report_title))
    state.conclusion?.let { c ->
        val (title, body) = verdictText(c.verdict)
        appendLine(context.getString(title))
        appendLine(context.getString(body))
        flags(c).forEach { appendLine("• " + context.getString(it)) }
    }
    CheckGroup.entries.forEach { group ->
        val rows = state.items.filter { it.group == group }
        if (rows.isEmpty()) return@forEach
        appendLine()
        appendLine("[" + context.getString(groupTitle(group)) + "]")
        rows.forEach { item ->
            val mark = when (item.status) {
                CheckStatus.OK -> "OK"
                CheckStatus.WARN -> "!!"
                CheckStatus.FAIL -> "XX"
                else -> "--"
            }
            val label = when (item.id) {
                "network" -> context.getString(R.string.netcheck_row_network)
                "gateway" -> context.getString(R.string.netcheck_row_gateway)
                "system_dns" -> context.getString(R.string.netcheck_row_system_dns)
                else -> item.label
            }
            val numbers = listOfNotNull(item.pingMs?.let { "ping ${it}ms" }, item.timeMs?.let { "${it}ms" }).joinToString(" ")
            val reason = item.fail?.let { context.getString(failText(it)) }.orEmpty()
            appendLine(listOf(mark, label, item.detail, numbers, reason).filter { it.isNotBlank() }.joinToString("  "))
        }
    }
}
