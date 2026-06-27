package com.qform.liveupdatenote.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a note in the local Room Database.
 *
 * @property id Auto-generated unique identifier.
 * @property text The text content of the note.
 * @property isActive Flag indicating if this note is currently promoted to the Live Update notification.
 */
@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val text: String,
    val isActive: Boolean = false
)
