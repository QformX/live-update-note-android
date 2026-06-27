package com.qform.liveupdatenote

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.qform.liveupdatenote.data.NoteDatabase
import com.qform.liveupdatenote.data.NoteRepository
import androidx.compose.runtime.collectAsState
import com.qform.liveupdatenote.ui.ThemeMode
import com.qform.liveupdatenote.ui.NoteViewModel
import com.qform.liveupdatenote.ui.NoteViewModelFactory
import com.qform.liveupdatenote.ui.screens.MainScreen
import com.qform.liveupdatenote.ui.theme.LUNTheme
import kotlinx.coroutines.launch

/**
 * Main Activity of the LUN app.
 * Configures compose, requests notifications permissions on Android 13+,
 * and automatically starts the Live Update Foreground Service if there is an active note.
 */
class MainActivity : ComponentActivity() {

    private val database by lazy { NoteDatabase.getDatabase(applicationContext) }
    private val repository by lazy { NoteRepository(database.noteDao) }

    private val viewModel: NoteViewModel by viewModels {
        NoteViewModelFactory(application, repository)
    }

    // Compose state to track permission
    private var hasNotificationPermission by mutableStateOf(false)
    private var isLiveUpdatesPromotedEnabled by mutableStateOf(true)

    // Standard Android Activity Launcher for permission requests
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasNotificationPermission = isGranted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply saved locale configuration on startup
        viewModel.applyLocale(this, viewModel.language.value)

        checkNotificationPermission()
        checkLiveUpdatesPromotionEnabled()

        // Check if there is an active note in the local database on startup.
        // If so, restore the Live Update foreground notification immediately.
        lifecycleScope.launch {
            val activeNote = repository.getActiveNoteDirect()
            if (activeNote != null) {
                viewModel.startLiveUpdateService(this@MainActivity)
            }
        }

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val isDark = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            LUNTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        viewModel = viewModel,
                        hasNotificationPermission = hasNotificationPermission,
                        isLiveUpdatesPromotedEnabled = isLiveUpdatesPromotedEnabled,
                        onRequestPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onOpenPromotionSettings = {
                            try {
                                val intent = android.content.Intent("android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS").apply {
                                    putExtra("android.provider.extra.APP_PACKAGE", packageName)
                                }
                                startActivity(intent)
                            } catch (e: Exception) {
                                // Fallback to main settings if the promotion settings screen isn't found
                                try {
                                    val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = android.net.Uri.fromParts("package", packageName, null)
                                    }
                                    startActivity(intent)
                                } catch (ex: Exception) {}
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check permission if user returns from settings
        checkNotificationPermission()
        checkLiveUpdatesPromotionEnabled()
    }

    private fun checkNotificationPermission() {
        hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun checkLiveUpdatesPromotionEnabled() {
        isLiveUpdatesPromotedEnabled = if (Build.VERSION.SDK_INT >= 36) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            try {
                val method = android.app.NotificationManager::class.java.getMethod("canPostPromotedNotifications")
                method.invoke(notificationManager) as Boolean
            } catch (e: Exception) {
                true
            }
        } else {
            true
        }
    }
}
