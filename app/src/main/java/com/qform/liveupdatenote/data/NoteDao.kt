package com.qform.liveupdatenote.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes ORDER BY id DESC")
    fun getAllNotesFlow(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isActive = 1 LIMIT 1")
    fun getActiveNoteFlow(): Flow<Note?>

    @Query("SELECT * FROM notes WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveNoteDirect(): Note?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    @Update
    suspend fun updateNote(note: Note)

    @Delete
    suspend fun deleteNote(note: Note)

    @Query("UPDATE notes SET isActive = 0")
    suspend fun deactivateAllNotes()

    @Query("UPDATE notes SET isActive = 1 WHERE id = :id")
    suspend fun activateNoteById(id: Long)

    @Query("UPDATE notes SET currentSteps = MIN(totalSteps, currentSteps + 1) WHERE id = :id")
    suspend fun incrementCurrentSteps(id: Long)

    @Query("UPDATE notes SET currentSteps = 0 WHERE id = :id")
    suspend fun resetCurrentSteps(id: Long)

    /**
     * Transaction to update the active note.
     * Only one note can be marked active. If [id] is null, all notes are deactivated.
     */
    @Transaction
    suspend fun setActiveNote(id: Long?) {
        deactivateAllNotes()
        if (id != null) {
            activateNoteById(id)
        }
    }
}
