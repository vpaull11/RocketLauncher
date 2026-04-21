package com.rocketlauncher.presentation.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val Primary = Color(0xFF0088CC)
private val PrimaryDark = Color(0xFF006699)
private val OnPrimary = Color.White
private val SurfaceLight = Color(0xFFF5F5F5)
private val SurfaceDark = Color(0xFF121212)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = Primary.copy(alpha = 0.2f),
    onPrimaryContainer = Color(0xFF001D35),
    surface = Color.White,
    surfaceVariant = SurfaceLight,
    background = Color.White,
    onSurface = Color(0xFF1C1B1F),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E)
)

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryDark,
    onPrimaryContainer = Color(0xFFE1F2FC),
    surface = SurfaceDark,
    surfaceVariant = Color(0xFF2D2D2D),
    background = SurfaceDark,
    onSurface = Color(0xFFE6E1E5),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99)
)

@Composable
fun RocketLauncherTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val surface = colorScheme.surface.toArgb()
            @Suppress("DEPRECATION")
            window.statusBarColor = surface
            @Suppress("DEPRECATION")
            window.navigationBarColor = surface
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
