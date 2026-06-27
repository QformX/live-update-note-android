package com.qform.liveupdatenote.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.qform.liveupdatenote.data.NoteDatabase
import com.qform.liveupdatenote.data.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver responsible for receiving notification actions for HABIT type notes,
 * updating the DB value on a background thread, and refreshing the Live Update Service.
 */
class HabitActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        if (noteId == -1L) return

        val database = NoteDatabase.getDatabase(context.applicationContext)
        val repository = NoteRepository(database.noteDao)
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_INCREMENT -> {
                        repository.incrementCurrentSteps(noteId)
                    }
                    ACTION_RESET -> {
                        repository.resetCurrentSteps(noteId)
                    }
                }
                
                // Explicitly send ACTION_START command to Service to force-refresh the notification layout
                val serviceIntent = Intent(context, LiveUpdateService::class.java).apply {
                    action = LiveUpdateService.ACTION_START
                }
                context.startService(serviceIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_INCREMENT = "com.qform.liveupdatenote.action.HABIT_INCREMENT"
        const val ACTION_RESET = "com.qform.liveupdatenote.action.HABIT_RESET"
        const val EXTRA_NOTE_ID = "extra_note_id"
    }
}
