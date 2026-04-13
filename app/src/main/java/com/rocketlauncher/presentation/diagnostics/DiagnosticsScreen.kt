package com.rocketlauncher.presentation.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.rocketlauncher.BuildConfig
import com.rocketlauncher.util.CrashLogger

/**
 * Экран диагностики приложения.
 *
 * Показывает:
 * - Информацию о WebSocket-соединении (диагностический лог RealtimeMessageService)
 * - Лог ошибок из CrashLogger (последние крашы, ошибки с трейсами)
 * - Кнопки: копировать / поделиться / очистить лог
 *
 * Открывается через профиль → «Диагностика».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    viewModel: DiagnosticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Диагностика") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        shareLog(context, uiState.fullLog)
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Поделиться логом")
                    }
                    IconButton(onClick = {
                        viewModel.clearLog()
                        Toast.makeText(context, "Лог очищен", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Очистить лог")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // === Информация о сборке ===
            SectionHeader("Версия приложения")
            InfoCard {
                InfoRow("Версия", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                InfoRow("Режим", if (BuildConfig.DEBUG) "DEBUG" else "RELEASE")
                InfoRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                InfoRow("Устройство", "${Build.MANUFACTURER} ${Build.MODEL}")
            }

            Divider()

            // === WebSocket диагностика ===
            SectionHeader("WebSocket / Realtime")
            InfoCard {
                InfoRow("Состояние", uiState.wsConnectionState)
                InfoRow("WebSocket", if (uiState.wsHasSocket) "активен" else "null")
                InfoRow("Авторизован", if (uiState.wsLoginDone) "да" else "нет")
                InfoRow("Должен быть подключён", if (uiState.wsShouldBeConnected) "да" else "нет")
                InfoRow("Подписок (комнат)", uiState.wsSubscriptionsCount.toString())
                InfoRow("Получено сообщений", uiState.wsReceivedMsgCount.toString())
                InfoRow("Сеть online", if (uiState.networkOnline) "да" else "нет")
                uiState.wsLastError?.let {
                    InfoRow("Последняя ошибка", it, isError = true)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { viewModel.refresh() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Обновить")
                }
                OutlinedButton(
                    onClick = {
                        copyToClipboard(context, uiState.wsDiagText, "WS диагностика")
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Копировать WS лог")
                }
            }

            // WS event log
            if (uiState.wsEventLog.isNotEmpty()) {
                SectionHeader("WS события (последние 50)")
                LogBox(uiState.wsEventLog)
            }

            Divider()

            // === Лог ошибок (CrashLogger) ===
            SectionHeader("Лог ошибок / крашей")

            if (uiState.crashLog.isBlank() || uiState.crashLog == "(лог пуст)") {
                Text(
                    "Ошибок не зафиксировано ✓",
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { copyToClipboard(context, uiState.crashLog, "Лог ошибок") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Копировать")
                    }
                    Button(
                        onClick = { shareLog(context, uiState.fullLog) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Поделиться")
                    }
                }
                Spacer(Modifier.height(4.dp))
                LogBox(uiState.crashLog, maxLines = 500)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, isError: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LogBox(text: String, maxLines: Int = 100) {
    val hScroll = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                MaterialTheme.shapes.small
            )
            .padding(8.dp)
            .horizontalScroll(hScroll)
    ) {
        Text(
            text = text.lines().takeLast(maxLines).joinToString("\n"),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                lineHeight = 14.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun copyToClipboard(context: Context, text: String, label: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "Скопировано в буфер", Toast.LENGTH_SHORT).show()
}

private fun shareLog(context: Context, text: String) {
    // Сначала пробуем поделиться через файл (для больших логов)
    val logFile = CrashLogger.getLogFile()
    if (logFile != null) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                logFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "RocketLauncher Crash Log")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Поделиться логом"))
            return
        } catch (_: Exception) {}
    }
    // Fallback: текстом
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, "RocketLauncher Crash Log")
    }
    context.startActivity(Intent.createChooser(intent, "Поделиться логом"))
}
