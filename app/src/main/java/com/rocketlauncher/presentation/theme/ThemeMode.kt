package com.rocketlauncher.presentation.theme

import androidx.annotation.StringRes
import com.rocketlauncher.R

/**
 * Режим оформления приложения.
 */
enum class ThemeMode(@StringRes val titleRes: Int) {
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark),
    SYSTEM(R.string.theme_system)
}
