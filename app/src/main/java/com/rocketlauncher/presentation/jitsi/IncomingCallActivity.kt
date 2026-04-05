package com.rocketlauncher.presentation.jitsi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.rocketlauncher.data.notifications.IncomingJitsiCallNotifier
import com.rocketlauncher.data.notifications.NotificationConstants
import com.rocketlauncher.R
import com.rocketlauncher.data.repository.VideoConferenceRepository
import com.rocketlauncher.presentation.theme.RocketLauncherTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Полноэкранный входящий видеозвонок (аналог входящего в мессенджерах).
 */
@AndroidEntryPoint
class IncomingCallActivity : ComponentActivity() {

    @Inject
    lateinit var videoConferenceRepository: VideoConferenceRepository

    @Inject
    lateinit var incomingJitsiCallNotifier: IncomingJitsiCallNotifier

    private var ringtone: Ringtone? = null

    private var roomId: String = ""

    private val endReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val rid = intent?.getStringExtra(EXTRA_ROOM_ID) ?: return
            if (rid == roomId) {
                stopRingtone()
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        roomId = intent.getStringExtra(EXTRA_ROOM_ID).orEmpty()
        val roomName = intent.getStringExtra(EXTRA_ROOM_NAME).orEmpty()
        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME).orEmpty()

        if (roomId.isBlank()) {
            finish()
            return
        }

        val filter = IntentFilter(NotificationConstants.ACTION_JITSI_CALL_ENDED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(endReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(endReceiver, filter)
        }

        setContent {
            RocketLauncherTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0D1117)
                ) {
                    IncomingCallScreen(
                        roomTitle = roomName.ifBlank { getString(R.string.incoming_call_default_room) },
                        callerLabel = callerName.ifBlank {
                            getString(R.string.incoming_call_default_caller)
                        },
                        onDecline = {
                            stopRingtone()
                            incomingJitsiCallNotifier.cancelForRoom(roomId)
                            finish()
                        },
                        onAccept = { acceptAndJoin() }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(this, uri)
            ringtone?.play()
        } catch (_: Exception) {
        }
    }

    override fun onResume() {
        super.onResume()
        incomingJitsiCallNotifier.cancelNotificationOnly(roomId)
    }

    override fun onDestroy() {
        stopRingtone()
        try {
            unregisterReceiver(endReceiver)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    private fun stopRingtone() {
        try {
            ringtone?.stop()
        } catch (_: Exception) {
        }
        ringtone = null
    }

    private fun acceptAndJoin() {
        stopRingtone()
        incomingJitsiCallNotifier.cancelNotificationOnly(roomId)
        lifecycleScope.launch {
            val result = videoConferenceRepository.startCallAndGetJoinUrl(roomId)
            result.fold(
                onSuccess = { url ->
                    try {
                        JitsiMeetLauncher.launch(this@IncomingCallActivity, url)
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@IncomingCallActivity,
                            e.message ?: getString(R.string.incoming_call_open_failed),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    finish()
                },
                onFailure = { e ->
                    Toast.makeText(
                        this@IncomingCallActivity,
                        e.message ?: getString(R.string.incoming_call_connect_failed),
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            )
        }
    }

    companion object {
        const val EXTRA_ROOM_ID = "incoming_room_id"
        const val EXTRA_ROOM_NAME = "incoming_room_name"
        const val EXTRA_ROOM_TYPE = "incoming_room_type"
        const val EXTRA_AVATAR_PATH = "incoming_avatar_path"
        const val EXTRA_CALLER_NAME = "incoming_caller_name"
    }
}

@Composable
private fun IncomingCallScreen(
    roomTitle: String,
    callerLabel: String,
    onDecline: () -> Unit,
    onAccept: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = roomTitle,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = callerLabel,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            textAlign = TextAlign.Center,
            fontSize = 26.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.incoming_call_heading),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(56.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onDecline,
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    Icons.Default.CallEnd,
                    contentDescription = stringResource(R.string.incoming_call_decline),
                    modifier = Modifier.size(36.dp),
                    tint = Color.White
                )
            }
            Button(
                onClick = onAccept,
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF43A047)),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    Icons.Default.Call,
                    contentDescription = stringResource(R.string.incoming_call_accept),
                    modifier = Modifier.size(36.dp),
                    tint = Color.White
                )
            }
        }
    }
}
