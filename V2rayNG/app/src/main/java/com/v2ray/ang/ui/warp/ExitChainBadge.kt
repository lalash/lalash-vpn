package com.v2ray.ang.ui.warp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.handler.ExitChain
import com.v2ray.ang.handler.WarpSettings
import com.v2ray.ang.ui.compose.colorConfigType
import com.v2ray.ang.ui.compose.colorFabActive
import com.v2ray.ang.ui.dashboard.flagEmoji
import java.util.Locale

/** Small notice that traffic leaves through WARP or Psiphon behind the selected config. */
@Composable
fun ExitChainBadge(chain: ExitChain, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = colorFabActive.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, colorFabActive.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                painterResource(R.drawable.ic_link_24dp),
                contentDescription = null,
                tint = colorFabActive,
                modifier = Modifier.size(16.dp)
            )
            Text(
                stringResource(R.string.chain_badge, exitChainLabel(chain)),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                stringResource(R.string.chain_badge_change),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colorConfigType
            )
        }
    }
}

/** "WARP" or "Psiphon · flag country" for the chain's exit. */
@Composable
fun exitChainLabel(chain: ExitChain): String {
    if (chain.exit == WarpSettings.CHAIN_WARP) return stringResource(R.string.chain_warp)
    val psiphon = stringResource(R.string.chain_psiphon)
    val where = if (chain.region.isEmpty()) {
        stringResource(R.string.engine_psiphon_region_auto)
    } else {
        "${flagEmoji(chain.region)} ${countryName(chain.region)}"
    }
    return "$psiphon · $where"
}

/** Country name for an ISO 3166 code in the app's language. */
fun countryName(code: String): String =
    Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.getDefault())
