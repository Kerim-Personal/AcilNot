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
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Query("SELECT * FROM notes WHERE id = :noteId")
    suspend fun getNoteById(noteId: Int): Note?

    // Sadece widget'a sabitlenmiş ve silinmemiş notları getir
    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND showOnWidget = 1 ORDER BY createdAt DESC") // GÜNCELLENDİ
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

    // YENİ: Tüm notların widget sabitlemesini kaldır
    @Query("UPDATE notes SET showOnWidget = 0")
    suspend fun unpinAllNotes()

    // YENİ: Belirtilen notların sabitleme durumunu ayarla
    @Query("UPDATE notes SET showOnWidget = :isPinned WHERE id IN (:noteIds)")
    suspend fun setPinnedStatus(noteIds: List<Int>, isPinned: Boolean)
}