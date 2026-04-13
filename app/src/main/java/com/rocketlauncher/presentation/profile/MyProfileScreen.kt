package com.rocketlauncher.presentation.profile

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.hilt.navigation.compose.hiltViewModel
import com.rocketlauncher.R

private data class StatusOption(val api: String, @StringRes val labelRes: Int)

private val STATUS_OPTIONS = listOf(
    StatusOption("online", R.string.status_online),
    StatusOption("away", R.string.status_away),
    StatusOption("busy", R.string.status_busy),
    StatusOption("offline", R.string.status_offline),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MyProfileScreen(
    onBack: () -> Unit,
    onOpenDiagnostics: (() -> Unit)? = null,
    viewModel: MyProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            Toast.makeText(
                context,
                context.getString(R.string.profile_saved_toast),
                Toast.LENGTH_SHORT
            ).show()
            viewModel.consumeSaveSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.loading && uiState.username.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.loadError != null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(uiState.loadError!!, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { viewModel.reload() }) {
                        Text(stringResource(R.string.profile_retry))
                    }
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (uiState.saving) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(
                        stringResource(R.string.profile_login_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (uiState.username.isNotEmpty()) "@${uiState.username}" else "—",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    OutlinedTextField(
                        value = uiState.name,
                        onValueChange = viewModel::onNameChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.profile_display_name)) },
                        singleLine = true,
                        enabled = !uiState.saving
                    )
                    OutlinedTextField(
                        value = uiState.nickname,
                        onValueChange = viewModel::onNicknameChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.profile_nickname)) },
                        singleLine = true,
                        enabled = !uiState.saving
                    )
                    OutlinedTextField(
                        value = uiState.bio,
                        onValueChange = viewModel::onBioChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.profile_bio)) },
                        minLines = 2,
                        enabled = !uiState.saving
                    )
                    Text(
                        stringResource(R.string.profile_status_section),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        STATUS_OPTIONS.forEach { opt ->
                            FilterChip(
                                selected = uiState.statusType == opt.api,
                                onClick = { viewModel.onStatusTypeChange(opt.api) },
                                label = { Text(stringResource(opt.labelRes)) },
                                enabled = !uiState.saving
                            )
                        }
                    }
                    OutlinedTextField(
                        value = uiState.statusText,
                        onValueChange = viewModel::onStatusTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.profile_status_text)) },
                        placeholder = { Text(stringResource(R.string.profile_status_placeholder)) },
                        singleLine = false,
                        minLines = 1,
                        enabled = !uiState.saving
                    )
                    uiState.saveError?.let { err ->
                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = { viewModel.save() },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        enabled = !uiState.saving
                    ) {
                        Text(
                            if (uiState.saving) {
                                stringResource(R.string.profile_saving)
                            } else {
                                stringResource(R.string.profile_save)
                            }
                        )
                    }
                    if (onOpenDiagnostics != null) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = onOpenDiagnostics,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Диагностика")
                        }
                    }
                }
            }
        }
    }
}
