package com.v2ray.ang.ui.usage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.colorConfigType
import com.v2ray.ang.ui.compose.colorFabActive

@Composable
fun UsageScreen(state: UsageUiState, onAction: (UsageAction) -> Unit, onBack: () -> Unit) {
    val uploadColor = MaterialTheme.colorScheme.onSurface
    Scaffold(
        topBar = { AppTopBar(title = stringResource(R.string.usage_title), onBackClick = onBack, isLoading = state.isLoading) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item(key = "range") {
                val labels = listOf(R.string.usage_range_today, R.string.usage_range_week, R.string.usage_range_month)
                SingleChoiceSegmentedButtonRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    UsageRange.entries.forEachIndexed { index, range ->
                        SegmentedButton(
                            selected = state.range == range,
                            onClick = { onAction(UsageAction.SelectRange(range)) },
                            shape = SegmentedButtonDefaults.itemShape(index, UsageRange.entries.size),
                        ) { Text(stringResource(labels[index])) }
                    }
                }
            }
            item(key = "total") { TotalCard(state) }
            item(key = "chart") { BarChartCard(state, uploadColor) }
            item(key = "per-config-title") {
                Text(
                    stringResource(R.string.usage_per_config),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colorConfigType,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)
                )
            }
            if (state.configs.isEmpty() && !state.isLoading) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.usage_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            val top = state.configs.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1
            items(state.configs, key = { it.guid }) { config ->
                ConfigUsageRow(config, top, uploadColor)
                ItemDivider()
            }
        }
    }
}

@Composable
private fun TotalCard(state: UsageUiState) {
    val rangeLabel = stringResource(
        when (state.range) {
            UsageRange.TODAY -> R.string.usage_range_today
            UsageRange.WEEK -> R.string.usage_range_week
            UsageRange.MONTH -> R.string.usage_range_month
        }
    )
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.usage_total, rangeLabel), style = MaterialTheme.typography.bodySmall)
                Text(
                    (state.totalUp + state.totalDown).toTrafficString(),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall.copy(textDirection = TextDirection.Ltr)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LegendLine(colorFabActive, stringResource(R.string.dashboard_download), state.totalDown.toTrafficString())
                LegendLine(MaterialTheme.colorScheme.inverseOnSurface, stringResource(R.string.dashboard_upload), state.totalUp.toTrafficString())
            }
        }
    }
}

@Composable
private fun LegendLine(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BarChartCard(state: UsageUiState, uploadColor: Color) {
    val max = state.bars.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1
    val description = stringResource(R.string.usage_chart_description)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .semantics { contentDescription = description },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                state.bars.forEach { bar ->
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clearAndSetSemantics {},
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        if (bar.total > 0) {
                            Text(
                                bar.total.toTrafficString(),
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                        val maxBar = 118f
                        Box(
                            Modifier
                                .fillMaxWidth(0.7f)
                                .height((bar.up.toFloat() / max * maxBar).dp)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(uploadColor)
                        )
                        Box(
                            Modifier
                                .fillMaxWidth(0.7f)
                                .height((bar.down.toFloat() / max * maxBar).dp)
                                .background(colorFabActive)
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.bars.forEach { bar ->
                    Text(
                        bar.label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigUsageRow(config: ConfigUsage, top: Long, uploadColor: Color) {
    Column(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                config.name.ifEmpty { stringResource(R.string.usage_deleted_config) },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            config.configType?.let {
                Text(it.name, style = MaterialTheme.typography.labelSmall, color = colorConfigType, fontWeight = FontWeight.SemiBold)
            }
            Text(config.total.toTrafficString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val downFraction = config.down.toFloat() / top
            val upFraction = config.up.toFloat() / top
            if (downFraction > 0f) Box(Modifier.fillMaxHeight().weight(downFraction).background(colorFabActive))
            if (upFraction > 0f) Box(Modifier.fillMaxHeight().weight(upFraction).background(uploadColor))
            val rest = 1f - downFraction - upFraction
            if (rest > 0.001f) Spacer(Modifier.weight(rest))
        }
        Spacer(Modifier.height(3.dp))
        Text(
            stringResource(R.string.usage_down_up, config.down.toTrafficString(), config.up.toTrafficString()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
