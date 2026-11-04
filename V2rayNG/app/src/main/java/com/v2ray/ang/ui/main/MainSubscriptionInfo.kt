package com.v2ray.ang.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.graphics.Color
import com.v2ray.ang.ui.compose.colorFabActive
import com.v2ray.ang.ui.compose.colorOnFabActive
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionUsageInfo
import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.handler.SubscriptionInfoParser
import com.v2ray.ang.util.Utils

private const val MILLIS_PER_SECOND = 1000L
private const val EXPIRY_DATE_PATTERN = "yyyy-MM-dd"
private const val NO_EXPIRY_SYMBOL = "\u221E"
private const val CARD_ALPHA = 0.97f

/** Locale-neutral values the subscription information box renders. */
internal data class SubscriptionInfoUiModel(
    val title: String?,
    val usedBytes: Long,
    /** Traffic quota in bytes; 0 means unlimited. */
    val totalBytes: Long,
    /** Used share of the quota in 0..1; null when the quota is unlimited. */
    val trafficFraction: Float?,
    /** Expiry in epoch milliseconds; null when the subscription never expires. */
    val expireMillis: Long?,
    val expired: Boolean,
    val supportUrl: String?,
    val webPageUrl: String?,
) {
    val trafficLow: Boolean get() = trafficFraction != null && trafficFraction >= TRAFFIC_WARNING_FRACTION

    companion object {
        const val TRAFFIC_WARNING_FRACTION = 0.9f
    }
}

internal fun buildSubscriptionInfoUiModel(
    info: SubscriptionUsageInfo,
    nowMillis: Long,
): SubscriptionInfoUiModel {
    val unlimited = info.total <= 0L
    val expireMillis = info.expire.takeIf { it > 0L }?.let { it * MILLIS_PER_SECOND }
    return SubscriptionInfoUiModel(
        title = info.title,
        usedBytes = info.used,
        totalBytes = if (unlimited) 0L else info.total,
        trafficFraction = if (unlimited) null else (info.used.toDouble() / info.total).toFloat().coerceIn(0f, 1f),
        expireMillis = expireMillis,
        expired = expireMillis != null && expireMillis <= nowMillis,
        supportUrl = info.supportUrl,
        webPageUrl = info.webPageUrl,
    )
}

/**
 * Fixed box on top of a subscription group showing the account information published by the
 * subscription panel: traffic, expiry date and shortcuts (support link, panel page, update).
 */
@Composable
internal fun SubscriptionInfoCard(
    info: SubscriptionUsageInfo?,
    fallbackTitle: String,
    onOpenLink: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    onEdit: (() -> Unit)? = null,
    onSpeedTest: (() -> Unit)? = null,
    onPing: (() -> Unit)? = null,
    isTesting: Boolean = false,
    autoLine: String = "",
) {
    // Panels that send no usage headers still get the card, without the figures.
    val model = remember(info) { info?.let { buildSubscriptionInfoUiModel(it, System.currentTimeMillis()) } }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        // Almost opaque: the list scrolls underneath without showing through readably.
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = CARD_ALPHA),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = model?.title ?: fallbackTitle,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                model?.supportUrl?.let { url ->
                    CardIconButton(
                        iconRes = if (SubscriptionInfoParser.isTelegramLink(url)) {
                            R.drawable.ic_telegram_24dp
                        } else {
                            R.drawable.ic_feedback_24dp
                        },
                        contentDescription = stringResource(R.string.sub_info_open_support),
                        onClick = { onOpenLink(url) },
                    )
                }
                model?.webPageUrl?.let { url ->
                    CardIconButton(
                        iconRes = R.drawable.ic_about_24dp,
                        contentDescription = stringResource(R.string.sub_info_open_page),
                        onClick = { onOpenLink(url) },
                    )
                }
                onEdit?.let {
                    CardIconButton(
                        iconRes = R.drawable.ic_edit_24dp,
                        contentDescription = stringResource(R.string.sub_edit_title),
                        onClick = it,
                    )
                }
                CardIconButton(
                    iconRes = R.drawable.ic_refresh_24dp,
                    contentDescription = stringResource(R.string.title_sub_update),
                    onClick = onRefresh,
                )
            }

            // One semantics node for the read-only figures so TalkBack reads them in one go.
            if (model != null) Column(
                Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp)
                    .semantics(mergeDescendants = true) {}
            ) {
                StatsRow(model)
                model.trafficFraction?.let { fraction ->
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .clearAndSetSemantics {},
                        color = if (model.trafficLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
            if (onSpeedTest != null || onPing != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    onSpeedTest?.let {
                        Button(
                            onClick = it,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colorFabActive, contentColor = colorOnFabActive),
                            contentPadding = PaddingValues(start = 10.dp, end = 14.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(painterResource(R.drawable.ic_speed_24dp), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.main_speed_test), fontWeight = FontWeight.Bold)
                        }
                    }
                    onPing?.let {
                        OutlinedButton(
                            onClick = it,
                            enabled = !isTesting,
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(start = 10.dp, end = 14.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(painterResource(R.drawable.ic_ping_24dp), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.main_ping_now), fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (autoLine.isNotEmpty()) {
                        Text(
                            autoLine,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardIconButton(iconRes: Int, contentDescription: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(painterResource(iconRes), contentDescription, Modifier.size(20.dp))
    }
}

@Composable
private fun StatsRow(model: SubscriptionInfoUiModel) {
    val totalText = if (model.totalBytes > 0L) {
        model.totalBytes.toTrafficString()
    } else {
        stringResource(R.string.sub_info_unlimited)
    }
    // Figures and the slash between them always read left to right, also in right-to-left languages;
    // otherwise the bidi algorithm swaps "used / total" around the slash.
    val figuresStyle = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = stringResource(R.string.sub_info_traffic, model.usedBytes.toTrafficString(), totalText),
            modifier = Modifier.weight(1f, fill = false),
            style = figuresStyle,
            color = if (model.trafficLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            // No expiry date means the subscription never expires.
            text = model.expireMillis?.let { Utils.formatTimestamp(it, EXPIRY_DATE_PATTERN) } ?: NO_EXPIRY_SYMBOL,
            style = figuresStyle,
            color = if (model.expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
