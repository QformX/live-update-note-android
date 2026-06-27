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
import com.qform.liveupdatenote.ui.NoteViewModel
import com.qform.liveupdatenote.ui.NoteViewModelFactory
import com.qform.liveupdatenote.ui.screens.MainScreen
import com.qform.liveupdatenote.ui.theme.LiveUpdateNoteTheme
import kotlinx.coroutines.launch

/**
 * Main Activity of the Live Update Note app.
 * Configures compose, requests notifications permissions on Android 13+,
 * and automatically starts the Live Update Foreground Service if there is an active note.
 */
class MainActivity : ComponentActivity() {

    private val database by lazy { NoteDatabase.getDatabase(applicationContext) }
    private val repository by lazy { NoteRepository(database.noteDao) }

    private val viewModel: NoteViewModel by viewModels {
        NoteViewModelFactory(repository)
    }

    // Compose state to track permission
    private var hasNotificationPermission by mutableStateOf(false)

    // Standard Android Activity Launcher for permission requests
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasNotificationPermission = isGranted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkNotificationPermission()

        // Check if there is an active note in the local database on startup.
        // If so, restore the Live Update foreground notification immediately.
        lifecycleScope.launch {
            val activeNote = repository.getActiveNoteDirect()
            if (activeNote != null) {
                viewModel.startLiveUpdateService(this@MainActivity)
            }
        }

        setContent {
            LiveUpdateNoteTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        viewModel = viewModel,
                        hasNotificationPermission = hasNotificationPermission,
                        onRequestPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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
}
