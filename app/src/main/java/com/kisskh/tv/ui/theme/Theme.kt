package com.kisskh.tv.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Shapes
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

private val LocalKhTypography = staticCompositionLocalOf { khTypography() }
private val LocalKhSpacing = staticCompositionLocalOf { KhSpacing() }
private val LocalKhShapes = staticCompositionLocalOf { KhShapes() }
private val LocalKhFocus = staticCompositionLocalOf { KhFocusSpec() }

/** Read KH tokens inside [KhTheme]: `KhTheme.typography.title`, `KhTheme.spacing.m`, `KhColors.Brand`. */
object KhTheme {
    val colors: KhColors get() = KhColors
    val typography: KhTypography @Composable @ReadOnlyComposable get() = LocalKhTypography.current
    val spacing: KhSpacing @Composable @ReadOnlyComposable get() = LocalKhSpacing.current
    val shapes: KhShapes @Composable @ReadOnlyComposable get() = LocalKhShapes.current
    val focus: KhFocusSpec @Composable @ReadOnlyComposable get() = LocalKhFocus.current
}

/** The dark KH colour scheme for tv-material3 components, mapped from [KhColors]. */
fun khColorScheme(): ColorScheme = darkColorScheme(
    primary = KhColors.TextPrimary, // white primary buttons...
    onPrimary = KhColors.OnLight, // ...with dark text
    primaryContainer = KhColors.Elevated,
    onPrimaryContainer = KhColors.TextPrimary,
    secondary = KhColors.Elevated,
    onSecondary = KhColors.TextPrimary,
    secondaryContainer = KhColors.Elevated,
    onSecondaryContainer = KhColors.TextPrimary,
    tertiary = KhColors.Brand, // accent
    onTertiary = KhColors.OnBrand,
    tertiaryContainer = KhColors.Brand,
    onTertiaryContainer = KhColors.OnBrand,
    background = KhColors.Background,
    onBackground = KhColors.TextPrimary,
    surface = KhColors.Surface,
    onSurface = KhColors.TextPrimary,
    surfaceVariant = KhColors.Elevated,
    onSurfaceVariant = KhColors.TextSecondary,
    error = KhColors.Brand,
    onError = KhColors.OnBrand,
    border = KhColors.Outline,
    borderVariant = KhColors.Elevated,
    scrim = KhColors.Scrim,
)

/** True on Android TV (including Chromecast with Google TV). */
fun isTelevision(context: Context): Boolean =
    (context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType ==
        Configuration.UI_MODE_TYPE_TELEVISION

/**
 * Wrap the whole app in this. It sets the dark colour scheme, text styles, shapes, spacing and focus
 * look. On a phone narrower than 600 dp, text is a little smaller and margins tighter.
 */
@Composable
fun KhTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isTv = remember(context) { isTelevision(context) }
    val compact = !isTv && LocalConfiguration.current.screenWidthDp < 600
    val typography = remember(compact) { khTypography(if (compact) 0.85f else 1f) }
    val spacing = remember(isTv, compact) { khSpacing(isTv, compact) }
    val shapes = remember { KhShapes() }

    MaterialTheme(
        colorScheme = khColorScheme(),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(4.dp),
            small = shapes.button,
            medium = shapes.card,
            large = shapes.tile,
            extraLarge = RoundedCornerShape(16.dp),
        ),
        typography = Typography(
            displayLarge = typography.display,
            displayMedium = typography.display,
            displaySmall = typography.display,
            headlineLarge = typography.headline,
            headlineMedium = typography.headline,
            headlineSmall = typography.headline,
            titleLarge = typography.title,
            titleMedium = typography.title,
            titleSmall = typography.label,
            bodyLarge = typography.body,
            bodyMedium = typography.body,
            bodySmall = typography.caption,
            labelLarge = typography.label,
            labelMedium = typography.label,
            labelSmall = typography.caption,
        ),
    ) {
        CompositionLocalProvider(
            LocalKhTypography provides typography,
            LocalKhSpacing provides spacing,
            LocalKhShapes provides shapes,
            LocalKhFocus provides KhFocusSpec(),
            LocalContentColor provides KhColors.TextPrimary,
            LocalTextStyle provides typography.body,
            content = content,
        )
    }
}
