package com.v2ray.ang.ui.compose

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val LightColor = lightColorScheme(
    primary = Color(0xFF000000), // Black
    onPrimary = Color(0xFFFFFFFF), // White
    primaryContainer = Color(0xFFE0E0E0), // Light Gray
    onPrimaryContainer = Color(0xFF000000), // Black
    secondary = Color(0xFFf97910), // Orange
    onSecondary = Color(0xFFFFFFFF), // White
    secondaryContainer = Color(0xFFFFE8D6), // Pale Orange
    onSecondaryContainer = Color(0xFF2B1700), // Dark Brown
    tertiary = Color(0xFF009966), // Green
    onTertiary = Color(0xFFFFFFFF), // White
    tertiaryContainer = Color(0xFFA0F2D0), // Light Green
    onTertiaryContainer = Color(0xFF00201A), // Dark Teal
    error = Color(0xFFBA1A1A), // Red
    errorContainer = Color(0xFFFFDAD6), // Light Red
    onError = Color(0xFFFFFFFF), // White
    onErrorContainer = Color(0xFF410002), // Dark Red
    background = Color(0xFFFFFFFF), // White
    onBackground = Color(0xFF1C1B1F), // Near Black
    surface = Color(0xFFFFFFFF), // White
    onSurface = Color(0xFF1C1B1F), // Near Black
    surfaceVariant = Color(0xFFE7E0EC), // Light Purple Gray
    onSurfaceVariant = Color(0xFF49454F), // Dark Gray
    outline = Color(0xFF79747E), // Medium Gray
    outlineVariant = Color(0xFFCAC4D0), // Light Gray
    inverseSurface = Color(0xFF313033), // Dark Gray
    inverseOnSurface = Color(0xFFF4EFF4), // Very Light Gray
    inversePrimary = Color(0xFFC0C0C0), // Silver Gray
    scrim = Color(0xFF000000), // Black
    surfaceTint = Color(0xFF000000), // Black
    surfaceContainerLowest = Color(0xFFFFFFFF), // White
    surfaceContainerLow = Color(0xFFF7F7F7), // Very Light Gray
    surfaceContainer = Color(0xFFF1F1F1), // Light Gray
    surfaceContainerHigh = Color(0xFFEBEBEB), // Light Gray
    surfaceContainerHighest = Color(0xFFE5E5E5), // Light Gray
)

private val DarkColor = darkColorScheme(
    primary = Color(0xFFC0C0C0), // Silver Gray
    onPrimary = Color(0xFF303030), // Dark Gray
    primaryContainer = Color(0xFF474747), // Gray
    onPrimaryContainer = Color(0xFFE0E0E0), // Light Gray
    secondary = Color(0xFFf97910), // Orange
    onSecondary = Color(0xFF4E2600), // Dark Brown
    secondaryContainer = Color(0xFF6F3800), // Brown
    onSecondaryContainer = Color(0xFFFFE8D6), // Pale Orange
    tertiary = Color(0xFF83D6B5), // Mint Green
    onTertiary = Color(0xFF00382E), // Dark Teal
    tertiaryContainer = Color(0xFF005143), // Teal
    onTertiaryContainer = Color(0xFFA0F2D0), // Light Green
    error = Color(0xFFFFB4AB), // Light Red
    errorContainer = Color(0xFF93000A), // Dark Red
    onError = Color(0xFF690005), // Deep Red
    onErrorContainer = Color(0xFFFFDAD6), // Light Red
    background = Color(0xFF1C1B1F), // Near Black
    onBackground = Color(0xFFE6E1E5), // Light Gray
    surface = Color(0xFF1C1B1F), // Near Black
    onSurface = Color(0xFFE6E1E5), // Light Gray
    surfaceVariant = Color(0xFF49454F), // Dark Gray
    onSurfaceVariant = Color(0xFFCAC4D0), // Light Gray
    outline = Color(0xFF938F99), // Grayish Purple
    outlineVariant = Color(0xFF49454F), // Dark Gray
    inverseSurface = Color(0xFFE6E1E5), // Light Gray
    inverseOnSurface = Color(0xFF1C1B1F), // Near Black
    inversePrimary = Color(0xFF000000), // Black
    scrim = Color(0xFF000000), // Black
    surfaceTint = Color(0xFFC0C0C0), // Silver Gray
    surfaceContainerLowest = Color(0xFF0F0F12), // Near Black
    surfaceContainerLow = Color(0xFF1A191D), // Dark Gray
    surfaceContainer = Color(0xFF1E1D21), // Dark Gray
    surfaceContainerHigh = Color(0xFF282729), // Dark Gray
    surfaceContainerHighest = Color(0xFF333234), // Dark Gray
)

/**
 * Accent palette over the white app: [accent] fills buttons and highlights, [accentText] is the
 * same hue dark enough for text and icons on white, [onAccent] is content on [accent], [tint] a
 * pale wash for selected surfaces, [ink] titles and dark buttons, [fab]/[onFab] the connect button.
 */
data class AppPalette(
    val key: String,
    val accent: Color,
    val accentText: Color,
    val onAccent: Color,
    val tint: Color,
    val ink: Color,
    val fab: Color,
    val onFab: Color,
)

object AppPalettes {
    val ORIGINAL = AppPalette("", Color(0xFFF97910), Color(0xFFF97910), Color.White, Color(0xFFFFE8D6), Color.Black, Color(0xFFF97910), Color.White)
    val NEON = AppPalette("neon", Color(0xFF22C55E), Color(0xFF15803D), Color(0xFF0A0A0A), Color(0xFFF0FDF4), Color(0xFF0A0A0A), Color(0xFF0A0A0A), Color(0xFF22C55E))
    val FOREST = AppPalette("forest", Color(0xFF10B981), Color(0xFF047857), Color(0xFF022C1E), Color(0xFFECFDF5), Color(0xFF064E3B), Color(0xFF10B981), Color(0xFF022C1E))
    val LIME = AppPalette("lime", Color(0xFF84CC16), Color(0xFF4D7C0F), Color(0xFF1A2E05), Color(0xFFF7FEE7), Color(0xFF1A2E05), Color(0xFF84CC16), Color(0xFF1A2E05))
    val EMERALD = AppPalette("emerald", Color(0xFF047857), Color(0xFF047857), Color.White, Color(0xFFECFDF5), Color(0xFF171717), Color(0xFF047857), Color.White)
    val TEAL = AppPalette("teal", Color(0xFF2DD4BF), Color(0xFF0F766E), Color(0xFF042F2E), Color(0xFFF0FDFA), Color(0xFF0F172A), Color(0xFF0F172A), Color(0xFF2DD4BF))
    val GREEN = AppPalette("green", Color(0xFF16A34A), Color(0xFF15803D), Color.White, Color(0xFFF0FDF4), Color(0xFF0A0A0A), Color(0xFF0A0A0A), Color(0xFF4ADE80))
    val ALL = listOf(ORIGINAL, NEON, FOREST, LIME, EMERALD, TEAL, GREEN)

    fun byKey(key: String?): AppPalette = ALL.firstOrNull { it.key == key } ?: ORIGINAL
}

val LocalAppPalette = compositionLocalOf { AppPalettes.ORIGINAL }

// Semantic Colors
val colorPing = Color(0xFF009966) // Green
val colorPingRed = Color(0xFFFF0099) // Pink Red

/** Accent text and icons; the dark shade on white, the bright one on the dark theme. */
val colorConfigType: Color
    @Composable @ReadOnlyComposable get() = LocalAppPalette.current.let { if (LocalDarkTheme.current) it.accent else it.accentText }

/** Accent fill for buttons, highlights and charts. */
val colorFabActive: Color
    @Composable @ReadOnlyComposable get() = LocalAppPalette.current.accent

/** Content (text, icons) on [colorFabActive]. */
val colorOnFabActive: Color
    @Composable @ReadOnlyComposable get() = LocalAppPalette.current.onAccent

/** The connect button while connected, and its icon. */
val colorConnect: Color
    @Composable @ReadOnlyComposable get() = LocalAppPalette.current.fab
val colorOnConnect: Color
    @Composable @ReadOnlyComposable get() = LocalAppPalette.current.onFab
val colorFabInactiveLight = Color(0xFF9C9C9C) // Gray
val colorFabInactiveDark = Color(0xFF646464) // Dark Gray
val dividerColorLight = Color(0xFFE0E0E0) // Light Gray
val dividerColorDark = Color(0xFF424242) // Dark Gray

// Toast Colors 85%
val toastNormalBgLight = Color(0xD9353A3E) // Dark Gray
val toastNormalBgDark = Color(0xD94A4F54) // Darker Gray
val toastSuccessBg = Color(0xD9388E3C) // Green
val toastErrorBg = Color(0xD9D50000) // Red
val toastInfoBg = Color(0xD93F51B5) // Indigo Blue
val toastIconCircleBg = Color(0x33FFFFFF) // Semi-transparent White
val toastTextColor = Color.White // White

object ThemeManager {
    private val _themeMode = MutableStateFlow(
        MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _dynamicColorEnabled = MutableStateFlow(
        MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, true)
    )
    val dynamicColorEnabled: StateFlow<Boolean> = _dynamicColorEnabled.asStateFlow()

    private val _palette = MutableStateFlow(
        AppPalettes.byKey(MmkvManager.decodeSettingsString(AppConfig.PREF_APP_PALETTE, ""))
    )
    val palette: StateFlow<AppPalette> = _palette.asStateFlow()

    fun setPalette(palette: AppPalette) {
        MmkvManager.encodeSettings(AppConfig.PREF_APP_PALETTE, palette.key)
        _palette.value = palette
    }

    fun setThemeMode(mode: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, mode)
        _themeMode.value = mode
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR, enabled)
        _dynamicColorEnabled.value = enabled
    }

    fun refresh() {
        _themeMode.value =
            MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
        _dynamicColorEnabled.value =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, true)
        _palette.value = AppPalettes.byKey(MmkvManager.decodeSettingsString(AppConfig.PREF_APP_PALETTE, ""))
    }
}

/** The fixed light (white) or dark scheme with the palette's accents in place of orange. */
private fun paletteScheme(palette: AppPalette, darkTheme: Boolean) = if (darkTheme) {
    DarkColor.copy(
        secondary = palette.accent,
        onSecondary = palette.onAccent,
        secondaryContainer = palette.accentText,
        onSecondaryContainer = palette.tint,
    )
} else {
    LightColor.copy(
        primary = palette.ink,
        surfaceTint = palette.ink,
        secondary = palette.accent,
        onSecondary = palette.onAccent,
        secondaryContainer = palette.tint,
        onSecondaryContainer = palette.ink,
    )
}

@Composable
fun resolveDarkTheme(): Boolean {
    val mode by ThemeManager.themeMode.collectAsState()
    return when (mode) {
        "1" -> false
        "2" -> true
        else -> isSystemInDarkTheme()
    }
}

val LocalDarkTheme = compositionLocalOf { false }

@Composable
fun AppTheme(
    darkTheme: Boolean = resolveDarkTheme(),
    content: @Composable () -> Unit
) {
    val dynamicColor by ThemeManager.dynamicColorEnabled.collectAsState()
    val palette by ThemeManager.palette.collectAsState()
    val context = LocalContext.current
    val colorScheme = when {
        // A chosen palette replaces wallpaper colors so the whole app follows it.
        palette != AppPalettes.ORIGINAL -> paletteScheme(palette, darkTheme)

        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColor
        else -> LightColor
    }
    val snackbarController = rememberAppSnackbarController()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalAppPalette provides palette,
        LocalAppSnackbar provides snackbarController
    ) {
        MaterialTheme(
            colorScheme = colorScheme
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AppSnackbarBridge(controller = snackbarController)
                content()
                AppSnackbarHost(hostState = snackbarController.hostState)
            }
        }
    }
}
