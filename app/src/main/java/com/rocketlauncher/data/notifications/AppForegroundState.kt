package com.rocketlauncher.data.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** true — приложение на переднем плане (любая активность видима). */
@Singleton
class AppForegroundState @Inject constructor() {
    private val _isInForeground = MutableStateFlow(false)
    val isInForeground: StateFlow<Boolean> = _isInForeground.asStateFlow()

    fun setForeground(foreground: Boolean) {
        _isInForeground.value = foreground
    }

    fun isForegroundNow(): Boolean = _isInForeground.value
}
