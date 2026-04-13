package com.rocketlauncher.presentation.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rocketlauncher.data.realtime.RealtimeMessageService
import com.rocketlauncher.util.CrashLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiagnosticsUiState(
    val wsConnectionState: String = "",
    val wsHasSocket: Boolean = false,
    val wsLoginDone: Boolean = false,
    val wsShouldBeConnected: Boolean = false,
    val wsSubscriptionsCount: Int = 0,
    val wsReceivedMsgCount: Int = 0,
    val wsLastError: String? = null,
    val networkOnline: Boolean = false,
    val wsEventLog: String = "",
    val wsDiagText: String = "",
    val crashLog: String = "",
    val fullLog: String = ""
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val realtimeService: RealtimeMessageService
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagnosticsUiState())
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    init {
        // Наблюдаем за состоянием соединения в реальном времени
        viewModelScope.launch {
            realtimeService.connectionState.collect { _ ->
                refresh()
            }
        }
    }

    fun refresh() {
        val diagText = realtimeService.getDiagnosticInfo()
        val wsEventLog = realtimeService.diagLog.value.joinToString("\n")
        val crashLog = CrashLogger.readLog()
        val fullLog = buildString {
            appendLine("=== RocketLauncher Diagnostics ===")
            appendLine(diagText)
            appendLine()
            appendLine("=== Crash/Error Log ===")
            appendLine(crashLog)
        }

        // Парсим диагностику для UI
        val state = realtimeService.connectionState.value.name
        val diagLines = diagText.lines()
        fun lineValue(key: String) = diagLines
            .firstOrNull { it.trimStart().startsWith("$key:") }
            ?.substringAfter("$key:")?.trim() ?: ""

        _uiState.update {
            it.copy(
                wsConnectionState = state,
                wsHasSocket = lineValue("ws") == "exists",
                wsLoginDone = lineValue("loginDone") == "true",
                wsShouldBeConnected = lineValue("shouldBeConnected") == "true",
                wsSubscriptionsCount = lineValue("subscriptions").toIntOrNull() ?: 0,
                wsReceivedMsgCount = realtimeService.receivedMsgCount,
                wsLastError = realtimeService.lastError,
                networkOnline = lineValue("network online") == "true",
                wsEventLog = wsEventLog,
                wsDiagText = diagText,
                crashLog = crashLog,
                fullLog = fullLog
            )
        }
    }

    fun clearLog() {
        CrashLogger.clearLog()
        _uiState.update { it.copy(crashLog = "(лог пуст)", fullLog = "") }
    }
}
