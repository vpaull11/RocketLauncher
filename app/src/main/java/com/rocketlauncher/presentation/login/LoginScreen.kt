package com.rocketlauncher.presentation.login

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rocketlauncher.R
import com.rocketlauncher.presentation.oauth.RocketChatOAuthActivity
import com.rocketlauncher.util.AppLog

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    var serverUrl by remember { mutableStateOf("https://open.rocket.chat") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val oauthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val token = result.data?.getStringExtra(RocketChatOAuthActivity.EXTRA_CREDENTIAL_TOKEN)
            val secret = result.data?.getStringExtra(RocketChatOAuthActivity.EXTRA_CREDENTIAL_SECRET)
            val cookies = result.data?.getStringExtra(RocketChatOAuthActivity.EXTRA_COOKIES)
            if (!token.isNullOrBlank() && !secret.isNullOrBlank()) {
                AppLog.d("RocketChatOAuth", "LoginScreen: получены credentials, tokenLen=${token.length}, secretLen=${secret.length}, hasCookies=${!cookies.isNullOrBlank()}")
                viewModel.handleOAuthCredentials(serverUrl, token, secret, cookies)
            } else {
                AppLog.w("RocketChatOAuth", "LoginScreen: RESULT_OK но credentials пусты: token=${token?.take(20)}, secret=${secret?.take(20)}")
            }
        } else {
            AppLog.d("RocketChatOAuth", "LoginScreen: OAuth отменён, resultCode=${result.resultCode}")
        }
    }

    if (uiState.isSuccess) {
        onLoginSuccess()
        return
    }

    val totpState = uiState.totpRequired
    if (totpState != null) {
        OtpDialog(
            method = totpState.details.method,
            emailOrUsername = totpState.details.emailOrUsername,
            codeGenerated = totpState.details.codeGenerated,
            isLoading = uiState.isLoading,
            error = uiState.error,
            onDismiss = { viewModel.dismissOtpDialog() },
            onSubmit = { viewModel.submitOtpCode(it) },
            onRequestCode = {
                viewModel.requestEmailCode { result ->
                    Toast.makeText(
                        context,
                        result.fold(
                            { context.getString(R.string.login_email_code_sent) },
                            {
                                context.getString(
                                    R.string.login_email_code_error,
                                    it.message ?: ""
                                )
                            }
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.login_headline),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(48.dp))

        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text(stringResource(R.string.login_server_url_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text(stringResource(R.string.login_username_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.login_password_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (uiState.error != null) {
            Text(
                text = uiState.error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = { viewModel.login(serverUrl, username, password) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading && serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(24.dp).padding(end = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            }
            Text(
                if (uiState.isLoading) {
                    stringResource(R.string.login_signing_in)
                } else {
                    stringResource(R.string.login_sign_in_password)
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Divider(modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.login_oauth_hint),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                scope.launch {
                    viewModel.setError(null)
                    viewModel.setLoadingOAuth(true)
                    val apiUrl = serverUrl.trimEnd('/') + "/api/v1/settings.oauth"
                    viewModel.getOAuthUrl(serverUrl)
                        .onSuccess { oauthUrl ->
                            Toast.makeText(
                                context,
                                context.getString(R.string.login_oauth_debug, apiUrl, oauthUrl),
                                Toast.LENGTH_LONG
                            ).show()
                            oauthLauncher.launch(
                                RocketChatOAuthActivity.createIntent(context, serverUrl, oauthUrl, apiUrl)
                            )
                        }
                        .onFailure {
                            val msg = it.message ?: context.getString(R.string.login_oauth_not_configured)
                            viewModel.setError(msg)
                            Toast.makeText(
                                context,
                                context.getString(R.string.error_generic_with_message, msg),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    viewModel.setLoadingOAuth(false)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading && !uiState.isLoadingOAuth && serverUrl.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            if (uiState.isLoadingOAuth) {
                CircularProgressIndicator(
                    modifier = Modifier.height(24.dp).padding(end = 8.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    strokeWidth = 2.dp
                )
            }
            Text(
                if (uiState.isLoadingOAuth) {
                    stringResource(R.string.login_oauth_loading)
                } else {
                    stringResource(R.string.login_oauth_button)
                }
            )
        }
    }
}

@Composable
private fun OtpDialog(
    method: String,
    emailOrUsername: String,
    codeGenerated: Boolean,
    isLoading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
    onRequestCode: () -> Unit
) {
    var otpCode by remember { mutableStateOf("") }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.login_otp_title)) },
        text = {
            Column {
                Text(
                    text = when (method) {
                        "email" -> stringResource(
                            R.string.login_otp_hint_email,
                            emailOrUsername
                        )
                        else -> stringResource(R.string.login_otp_hint_generic)
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = otpCode,
                    onValueChange = { otpCode = it },
                    label = { Text(stringResource(R.string.login_otp_code_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !isLoading
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (method == "email" && !codeGenerated) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onRequestCode,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Text(stringResource(R.string.login_otp_send_email))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(otpCode) },
                enabled = otpCode.isNotBlank() && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.action_confirm))
                }
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
