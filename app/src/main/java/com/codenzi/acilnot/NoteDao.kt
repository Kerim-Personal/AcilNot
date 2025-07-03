package com.codenzi.acilnot

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

    // Sadece silinmemiş notları getir
    @Query("SELECT * FROM notes WHERE isDeleted = 0 ORDER BY createdAt DESC")
    suspend fun getNotesForWidget(): List<Note>

    // Sadece silinmemiş notları getir
    @Query("SELECT * FROM notes WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun getAllNotes(): Flow<List<Note>>

    // Notu çöp kutusuna taşı (soft delete)
    @Query("UPDATE notes SET isDeleted = 1, deletedAt = :timestamp WHERE id = :noteId")
    suspend fun softDeleteById(noteId: Int, timestamp: Long)

    // Notları çöp kutusundan geri yükle
    @Query("UPDATE notes SET isDeleted = 0, deletedAt = NULL WHERE id IN (:noteIds)")
    suspend fun restoreNotes(noteIds: List<Int>)

    // Çöp kutusundaki notları getir
    @Query("SELECT * FROM notes WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun getDeletedNotes(): Flow<List<Note>>

    // Notu kalıcı olarak sil
    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun hardDeleteById(noteId: Int)
}