package com.v2ray.ang.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppPalette
import com.v2ray.ang.ui.compose.AppPalettes
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.ThemeManager
import com.v2ray.ang.ui.compose.colorConfigType
import com.v2ray.ang.ui.compose.colorConnect
import com.v2ray.ang.ui.compose.colorFabActive
import com.v2ray.ang.ui.compose.colorOnConnect
import com.v2ray.ang.ui.compose.colorOnFabActive

/**
 * Theme picker: the original orange or one of the green palettes. The choice applies to every
 * screen at once through [ThemeManager].
 */
class ThemeActivity : BaseComponentActivity() {

    @Composable
    override fun ScreenContent() {
        val current by ThemeManager.palette.collectAsState()
        Scaffold(
            topBar = { AppTopBar(title = stringResource(R.string.theme_title), onBackClick = { finish() }) }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        stringResource(R.string.theme_intro),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item { Preview() }
                items(AppPalettes.ALL, key = { it.key.ifEmpty { "original" } }) { palette ->
                    PaletteRow(palette, selected = palette == current) { ThemeManager.setPalette(palette) }
                }
            }
        }
    }

    /** A few app elements drawn in the current palette. */
    @Composable
    private fun Preview() {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.theme_preview),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colorConfigType
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(colorFabActive)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(stringResource(R.string.theme_preview_button), color = colorOnFabActive, fontWeight = FontWeight.Bold)
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, colorConfigType)
                    ) {
                        Text(
                            stringResource(R.string.theme_preview_chip),
                            color = colorConfigType,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    Box(Modifier.weight(1f))
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colorConnect),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(painterResource(R.drawable.ic_stop_24dp), contentDescription = null, tint = colorOnConnect)
                    }
                }
            }
        }
    }

    @Composable
    private fun PaletteRow(palette: AppPalette, selected: Boolean, onSelect: () -> Unit) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (selected) palette.tint else MaterialTheme.colorScheme.surface,
            border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) palette.accentText else MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                // Mini icon: the dark ink with the accent box, like the app icon.
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.ink),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .width(24.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(palette.accent)
                    )
                }
                Text(
                    stringResource(paletteName(palette)),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(palette.accent)
                )
                RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = palette.accentText))
            }
        }
    }

    private fun paletteName(palette: AppPalette): Int = when (palette) {
        AppPalettes.NEON -> R.string.theme_neon
        AppPalettes.FOREST -> R.string.theme_forest
        AppPalettes.LIME -> R.string.theme_lime
        AppPalettes.EMERALD -> R.string.theme_emerald
        AppPalettes.TEAL -> R.string.theme_teal
        AppPalettes.GREEN -> R.string.theme_green
        else -> R.string.theme_original
    }
}
