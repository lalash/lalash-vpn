package com.v2ray.ang.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.LocalAppPalette
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.compose.colorConfigType
import com.v2ray.ang.ui.compose.colorFabActive
import com.v2ray.ang.ui.compose.colorOnFabActive
import com.v2ray.ang.ui.compose.colorOnConnect
import com.v2ray.ang.ui.compose.colorConnect
import com.v2ray.ang.ui.compose.colorFabInactiveDark
import com.v2ray.ang.ui.compose.colorFabInactiveLight
import com.v2ray.ang.ui.compose.colorPing
import com.v2ray.ang.ui.warp.ExitChainBadge
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.max

private val uploadColorLight = Color(0xFF2F5DA8)
private val uploadColorDark = Color(0xFF8AB4F8)

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onAction: (DashboardAction) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = { AppTopBar(title = stringResource(R.string.dashboard_title), onBackClick = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ConnectHero(state, onToggle = { onAction(DashboardAction.ToggleService) })
            ConfigPingCard(state, onAction)
            state.exitChain?.let { chain ->
                ExitChainBadge(chain, onClick = { onAction(DashboardAction.OpenExitChain) }, modifier = Modifier.fillMaxWidth())
            }
            ExitCountryCard(state)
            TrafficCard(state)
            IranRouteCard(state.iranDirect) { onAction(DashboardAction.SetIranDirect(it)) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ConnectHero(state: DashboardUiState, onToggle: () -> Unit) {
    val dark = LocalDarkTheme.current
    val running = state.isRunning
    val ring = if (running) colorFabActive else MaterialTheme.colorScheme.onSurface
    val buttonColor = when {
        running -> colorConnect
        dark -> colorFabInactiveDark
        else -> colorFabInactiveLight
    }
    val label = stringResource(if (running) R.string.dashboard_disconnect else R.string.dashboard_connect)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(164.dp)
                .clip(CircleShape)
                .background(ring.copy(alpha = if (running) 0.10f else 0.04f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(136.dp)
                    .clip(CircleShape)
                    .background(ring.copy(alpha = if (running) 0.20f else 0.07f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    onClick = onToggle,
                    shape = CircleShape,
                    color = buttonColor,
                    contentColor = if (running) colorOnConnect else Color.White,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .size(108.dp)
                        .semantics { contentDescription = label }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(painterResource(R.drawable.ic_power_24dp), contentDescription = null, modifier = Modifier.size(40.dp))
                        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(if (running) R.string.dashboard_connected else R.string.connection_not_connected),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (running) colorPing else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (running && state.sessionStartMillis > 0) SessionTimer(state.sessionStartMillis)
        }
    }
}

@Composable
private fun SessionTimer(startMillis: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startMillis) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val seconds = max(0L, (now - startMillis) / 1000)
    Text(
        "%02d:%02d:%02d".format(Locale.US, seconds / 3600, seconds % 3600 / 60, seconds % 60),
        style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ConfigPingCard(state: DashboardUiState, onAction: (DashboardAction) -> Unit) {
    val dark = LocalDarkTheme.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (dark) colorFabActive.copy(alpha = 0.14f) else LocalAppPalette.current.tint,
        border = BorderStroke(1.5.dp, colorFabActive),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val openLabel = stringResource(R.string.dashboard_change_config)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClickLabel = openLabel) { onAction(DashboardAction.OpenConfigList) }
                    .padding(2.dp)
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colorFabActive),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(painterResource(R.drawable.ic_list_24dp), contentDescription = null, tint = colorOnFabActive)
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.dashboard_current_config), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            state.configName.ifEmpty { stringResource(R.string.dashboard_no_config) },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        state.configType?.let {
                            Text(it.name, style = MaterialTheme.typography.labelMedium, color = colorConfigType, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Text(openLabel, style = MaterialTheme.typography.labelLarge, color = colorConfigType, fontWeight = FontWeight.SemiBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onAction(DashboardAction.TestPing) },
                    enabled = state.isRunning,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Icon(painterResource(R.drawable.ic_ping_24dp), contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(if (state.pingsPending > 0) R.string.dashboard_ping_testing else R.string.dashboard_ping_test),
                        fontWeight = FontWeight.Bold
                    )
                }
                PingResultBox(state)
            }
        }
    }
}

@Composable
private fun PingResultBox(state: DashboardUiState) {
    val (value, label, color) = when (val ping = state.ping) {
        is DashboardPing.Result -> Triple(
            stringResource(R.string.server_test_delay_value, ping.millis),
            stringResource(
                when {
                    ping.millis < 300 -> R.string.dashboard_ping_excellent
                    ping.millis < 700 -> R.string.dashboard_ping_good
                    else -> R.string.dashboard_ping_slow
                }
            ),
            if (ping.millis < 700) colorPing else colorConfigType
        )
        DashboardPing.Failed -> Triple("×", stringResource(R.string.dashboard_ping_failed), MaterialTheme.colorScheme.error)
        DashboardPing.Testing -> Triple("…", stringResource(R.string.dashboard_ping_wait), MaterialTheme.colorScheme.onSurfaceVariant)
        DashboardPing.Idle -> Triple(
            "—",
            stringResource(if (state.isRunning) R.string.dashboard_ping_not_tested else R.string.connection_not_connected),
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .width(108.dp)
            .height(52.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = color, maxLines = 1)
            Text(label, fontSize = 11.sp, color = color, maxLines = 1)
        }
    }
}

@Composable
private fun ExitCountryCard(state: DashboardUiState) {
    val code = state.exitCountry?.trim()?.takeIf { it.length == 2 && it.all(Char::isLetter) }?.uppercase()
    val name = when {
        code != null -> Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.getDefault()).ifEmpty { code }
        !state.exitCountry.isNullOrBlank() -> state.exitCountry
        else -> stringResource(R.string.dashboard_unknown)
    }
    DashboardCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(width = 44.dp, height = 32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                if (code != null && state.isRunning) {
                    Text(flagEmoji(code), fontSize = 24.sp)
                } else {
                    Icon(painterResource(R.drawable.ic_globe_24dp), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.dashboard_exit_country), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (state.isRunning) name else stringResource(R.string.dashboard_unknown), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Text(
                state.exitIp?.takeIf { state.isRunning } ?: "—",
                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TrafficCard(state: DashboardUiState) {
    val upColor = if (LocalDarkTheme.current) uploadColorDark else uploadColorLight
    DashboardCard(contentPadding = 12.dp) {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpeedTile(
                    title = stringResource(R.string.dashboard_download),
                    iconRes = R.drawable.ic_arrow_downward_24dp,
                    color = colorFabActive,
                    bps = state.downloadBps,
                    sessionBytes = state.sessionDownloadBytes,
                    modifier = Modifier.weight(1f)
                )
                SpeedTile(
                    title = stringResource(R.string.dashboard_upload),
                    iconRes = R.drawable.ic_arrow_upward_24dp,
                    color = upColor,
                    bps = state.uploadBps,
                    sessionBytes = state.sessionUploadBytes,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            val chartDescription = stringResource(R.string.dashboard_chart_description)
            MirroredTrafficChart(
                download = state.downloadHistory,
                upload = state.uploadHistory,
                downColor = colorFabActive,
                upColor = upColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(124.dp)
                    .semantics { contentDescription = chartDescription }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.dashboard_chart_60s), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.dashboard_chart_now), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SpeedTile(title: String, iconRes: Int, color: Color, bps: Double, sessionBytes: Long, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(painterResource(iconRes), contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                formatSpeed(bps),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr)
            )
            Text(
                stringResource(R.string.dashboard_session_total, sessionBytes.toTrafficString()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Bytes per second as KB/s while below 1 MB/s, then MB/s. */
internal fun formatSpeed(bytesPerSecond: Double): String {
    val kb = bytesPerSecond / 1024
    return if (kb < 1024) "%.0f KB/s".format(Locale.US, kb) else "%.1f MB/s".format(Locale.US, kb / 1024)
}

/**
 * Download above the centre line, upload below it, each on its own scale. Time flows in the
 * reading direction: the newest point sits on the reading-end side (right in LTR, left in
 * RTL), matching the "now" label.
 */
@Composable
private fun MirroredTrafficChart(
    download: List<Float>,
    upload: List<Float>,
    downColor: Color,
    upColor: Color,
    modifier: Modifier,
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val axis = MaterialTheme.colorScheme.outline
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier) {
        val mid = size.height * 0.65f
        val downHeight = mid - 6f
        val upHeight = size.height - mid - 6f
        val downMax = max(1f, download.maxOrNull() ?: 0f) * 1.15f
        val upMax = max(0.5f, upload.maxOrNull() ?: 0f) * 1.15f
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))
        drawLine(grid, Offset(0f, 6f), Offset(size.width, 6f), pathEffect = dash)
        drawLine(grid, Offset(0f, size.height - 6f), Offset(size.width, size.height - 6f), pathEffect = dash)

        fun points(values: List<Float>, maxValue: Float, height: Float, sign: Float): List<Offset> {
            val step = size.width / (values.size - 1).coerceAtLeast(1)
            // Newest value is last; it goes to the reading-end edge.
            return values.mapIndexed { i, v ->
                val x = if (rtl) size.width - i * step else i * step
                Offset(x, mid + sign * (v / maxValue) * height)
            }
        }

        fun smooth(p: List<Offset>): Path = Path().apply {
            moveTo(p.first().x, p.first().y)
            for (i in 0 until p.size - 1) {
                val p0 = p.getOrElse(i - 1) { p[i] }
                val p1 = p[i]
                val p2 = p[i + 1]
                val p3 = p.getOrElse(i + 2) { p2 }
                cubicTo(
                    p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
                    p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
                    p2.x, p2.y
                )
            }
        }

        listOf(
            Triple(points(download, downMax, downHeight, -1f), downColor, true),
            Triple(points(upload, upMax, upHeight, 1f), upColor, false),
        ).forEach { (pts, color, isDown) ->
            val line = smooth(pts)
            val area = Path().apply {
                addPath(line)
                lineTo(pts.last().x, mid)
                lineTo(pts.first().x, mid)
                close()
            }
            drawPath(
                area,
                Brush.verticalGradient(
                    colors = if (isDown) listOf(color.copy(alpha = 0.45f), color.copy(alpha = 0.02f))
                    else listOf(color.copy(alpha = 0.02f), color.copy(alpha = 0.35f)),
                    startY = if (isDown) mid - downHeight else mid,
                    endY = if (isDown) mid else mid + upHeight
                )
            )
            drawPath(line, color, style = Stroke(width = if (isDown) 5f else 4f))
            drawCircle(color, radius = 9f, center = pts.last())
        }
        drawLine(axis, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 2f)
    }
}

@Composable
private fun IranRouteCard(enabled: Boolean, onChange: (Boolean) -> Unit) {
    val title = stringResource(R.string.dashboard_iran_route)
    val stateText = stringResource(if (enabled) R.string.dashboard_state_on else R.string.dashboard_state_off)
    DashboardCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = enabled, role = Role.Switch, onValueChange = onChange)
                .semantics(mergeDescendants = true) {
                    stateDescription = stateText
                    toggleableState = ToggleableState(enabled)
                }
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.dashboard_iran_route_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = enabled, onCheckedChange = null)
        }
    }
}

@Composable
private fun DashboardCard(contentPadding: androidx.compose.ui.unit.Dp = 14.dp, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(Modifier.padding(horizontal = contentPadding, vertical = 10.dp)) { content() }
    }
}

/** Regional-indicator flag for an ISO 3166 alpha-2 code, e.g. "NL" -> the Dutch flag. */
internal fun flagEmoji(code: String): String {
    val upper = code.uppercase(Locale.US)
    if (upper.length != 2 || !upper.all { it in 'A'..'Z' }) return ""
    return String(Character.toChars(0x1F1E6 + (upper[0] - 'A'))) + String(Character.toChars(0x1F1E6 + (upper[1] - 'A')))
}
