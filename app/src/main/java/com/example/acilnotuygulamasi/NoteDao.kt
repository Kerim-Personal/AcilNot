package com.example.acilnotuygulamasi

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: Note)

    @Update
    suspend fun update(note: Note)

    @Query("SELECT * FROM notes WHERE id = :noteId")
    suspend fun getNoteById(noteId: Int): Note?

    @Query("SELECT * FROM notes ORDER BY id DESC LIMIT 5")
    suspend fun getLatest5Notes(): List<Note>

    @Query("SELECT * FROM notes ORDER BY id DESC")
    fun getAllNotes(): Flow<List<Note>>

    // YENİ EKLENEN FONKSİYON
    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteById(noteId: Int)
}