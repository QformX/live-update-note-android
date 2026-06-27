package com.qform.liveupdatenote.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.qform.liveupdatenote.data.Note
import com.qform.liveupdatenote.data.NoteRepository
import com.qform.liveupdatenote.service.LiveUpdateService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Enums representing the active theme selection.
 */
enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

/**
 * ViewModel managing UI states, database CRUD, and app settings (Theme & Language).
 */
class NoteViewModel(
    application: Application,
    private val repository: NoteRepository
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    // Expose all notes flow as state
    val allNotes: StateFlow<List<Note>> = repository.allNotes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Expose active note flow as state
    val activeNote: StateFlow<Note?> = repository.activeNote.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    // Theme Mode state
    private val _themeMode = MutableStateFlow(getSavedThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    // Language state ("ru" or "en")
    private val _language = MutableStateFlow(getSavedLanguage())
    val language: StateFlow<String> = _language.asStateFlow()

    /**
     * Inserts a new note into the Room database.
     */
    fun insertNote(text: String, type: String = "TEXT", totalSteps: Int = 1) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.insert(Note(text = text.trim(), type = type, totalSteps = totalSteps))
        }
    }

    /**
     * Increments the progress steps of a habit.
     */
    fun incrementSteps(note: Note) {
        viewModelScope.launch {
            repository.incrementCurrentSteps(note.id)
        }
    }

    /**
     * Resets the progress steps of a habit.
     */
    fun resetSteps(note: Note) {
        viewModelScope.launch {
            repository.resetCurrentSteps(note.id)
        }
    }

    /**
     * Deletes a note from the Room database.
     */
    fun deleteNote(note: Note) {
        viewModelScope.launch {
            repository.delete(note)
        }
    }

    /**
     * Toggles the active status of a note.
     * Starts the Foreground Service if a note is activated.
     */
    fun toggleNoteActive(context: Context, note: Note) {
        viewModelScope.launch {
            val makeActive = !note.isActive
            if (makeActive) {
                repository.setActiveNote(note.id)
                startLiveUpdateService(context)
            } else {
                repository.setActiveNote(null)
            }
        }
    }

    /**
     * Helper to launch/start the Foreground Live Update Service.
     */
    fun startLiveUpdateService(context: Context) {
        val intent = Intent(context, LiveUpdateService::class.java).apply {
            action = LiveUpdateService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    // --- Settings Logic ---

    private fun getSavedThemeMode(): ThemeMode {
        val themeStr = prefs.getString("theme_mode", "system") ?: "system"
        return when (themeStr) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", when (mode) {
            ThemeMode.LIGHT -> "light"
            ThemeMode.DARK -> "dark"
            ThemeMode.SYSTEM -> "system"
        }).apply()
        _themeMode.value = mode
    }

    private fun getSavedLanguage(): String {
        return prefs.getString("language", "en") ?: "en"
    }

    fun setLanguage(context: Context, langCode: String) {
        prefs.edit().putString("language", langCode).apply()
        _language.value = langCode
        applyLocale(context, langCode)
    }

    fun applyLocale(context: Context, langCode: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                val localeManager = context.getSystemService(Context.LOCALE_SERVICE) as android.app.LocaleManager
                localeManager.applicationLocales = android.os.LocaleList.forLanguageTags(langCode)
            } catch (e: Exception) {
                // Fail-safe
            }
        } else {
            try {
                val locale = java.util.Locale(langCode)
                java.util.Locale.setDefault(locale)
                val resources = context.resources
                val config = resources.configuration
                config.setLocale(locale)
                resources.updateConfiguration(config, resources.displayMetrics)
            } catch (e: Exception) {
                // Fail-safe
            }
        }
    }
}

/**
 * ViewModel Factory to instantiate NoteViewModel with Application and Repository parameters.
 */
class NoteViewModelFactory(
    private val application: Application,
    private val repository: NoteRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NoteViewModel::class.java)) {
            return NoteViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
