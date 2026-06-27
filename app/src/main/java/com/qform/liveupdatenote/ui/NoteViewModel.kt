package com.qform.liveupdatenote.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.qform.liveupdatenote.data.Note
import com.qform.liveupdatenote.data.NoteRepository
import com.qform.liveupdatenote.service.LiveUpdateService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel managing UI states and actions for note management.
 */
class NoteViewModel(private val repository: NoteRepository) : ViewModel() {

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

    /**
     * Inserts a new note into the Room database.
     */
    fun insertNote(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.insert(Note(text = text.trim()))
        }
    }

    /**
     * Deletes a note from the Room database.
     */
    fun deleteNote(note: Note) {
        viewModelScope.launch {
            // If the deleted note was active, the flow will update and deactivate the service
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
                // Set this note active in Room
                repository.setActiveNote(note.id)
                // Start Foreground Service
                startLiveUpdateService(context)
            } else {
                // Deactivate all notes
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
}

/**
 * ViewModel Factory to instantiate NoteViewModel with a Repository parameter.
 */
class NoteViewModelFactory(private val repository: NoteRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NoteViewModel::class.java)) {
            return NoteViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
