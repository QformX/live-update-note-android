package com.qform.liveupdatenote.data

import kotlinx.coroutines.flow.Flow

/**
 * Repository coordinating Room Database CRUD operations and flows.
 */
class NoteRepository(private val noteDao: NoteDao) {

    val allNotes: Flow<List<Note>> = noteDao.getAllNotesFlow()
    val activeNote: Flow<Note?> = noteDao.getActiveNoteFlow()

    suspend fun getActiveNoteDirect(): Note? {
        return noteDao.getActiveNoteDirect()
    }

    suspend fun insert(note: Note): Long {
        return noteDao.insertNote(note)
    }

    suspend fun update(note: Note) {
        noteDao.updateNote(note)
    }

    suspend fun delete(note: Note) {
        noteDao.deleteNote(note)
    }

    suspend fun setActiveNote(id: Long?) {
        noteDao.setActiveNote(id)
    }
}
