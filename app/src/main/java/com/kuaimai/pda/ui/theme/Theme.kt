package com.kuaimai.pda.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 快麦取货通 Material3 主题
 */
private val LightColorScheme = lightColorScheme(
    primary = BrandBlue,
    onPrimary = SurfaceWhite,
    primaryContainer = PrimaryLightBg,
    onPrimaryContainer = PrimaryLightText,
    secondary = SuccessText,
    onSecondary = SurfaceWhite,
    secondaryContainer = SuccessBg,
    onSecondaryContainer = SuccessText,
    tertiary = DangerText,
    onTertiary = SurfaceWhite,
    tertiaryContainer = DangerBg,
    onTertiaryContainer = DangerText,
    background = SurfaceGray,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceGray,
    onSurfaceVariant = TextSecondary,
    outline = BorderGray,
    error = DangerText,
    onError = SurfaceWhite,
    errorContainer = DangerBg,
    onErrorContainer = DangerText
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandBlue,
    onPrimary = SurfaceWhite,
    secondary = SuccessText,
    tertiary = DangerText,
    background = TextPrimary,
    surface = TextPrimary,
    onBackground = SurfaceWhite,
    onSurface = SurfaceWhite
)

@Composable
fun KuaimaiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // 状态栏颜色跟随主题
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = BrandBlue.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
