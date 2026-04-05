package com.rocketlauncher.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.rocketlauncher.R
import com.rocketlauncher.data.repository.AuthRepository
import com.rocketlauncher.data.repository.ThemePreferences
import com.rocketlauncher.domain.usecase.SyncChatsFromServerUseCase
import com.rocketlauncher.presentation.navigation.NavViewModel
import com.rocketlauncher.presentation.navigation.RocketNavHost
import com.rocketlauncher.presentation.theme.RocketLauncherTheme
import com.rocketlauncher.presentation.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var syncChatsFromServerUseCase: SyncChatsFromServerUseCase

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var themePreferences: ThemePreferences

    private val navViewModel: NavViewModel by viewModels()

    private var returnedFromBackground = false

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* пользователь может отказать — уведомления тогда недоступны */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_RocketLauncher)
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        navViewModel.handleIntent(intent)

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                returnedFromBackground = true
            }

            override fun onStart(owner: LifecycleOwner) {
                if (returnedFromBackground) {
                    returnedFromBackground = false
                    if (authRepository.authState.value.isLoggedIn) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                syncChatsFromServerUseCase()
                            } catch (_: Exception) {
                                // тихо: список обновится при следующем заходе на экран чатов
                            }
                        }
                    }
                }
            }
        })

        enableEdgeToEdge()
        setContent {
            val themeMode by themePreferences.themeMode.collectAsStateWithLifecycle(
                initialValue = ThemeMode.SYSTEM
            )
            LaunchedEffect(themeMode) {
                AppCompatDelegate.setDefaultNightMode(
                    when (themeMode) {
                        ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                        ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                        ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }
                )
            }
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDark
            }
            RocketLauncherTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RocketNavHost(navViewModel = navViewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        navViewModel.handleIntent(intent)
    }
}
