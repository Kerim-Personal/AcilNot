package com.example.acilnotuygulamasi

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface NoteDao {

    // Yeni not ekle veya var olanı değiştir
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: Note)

    // Notu güncelle
    @Update
    suspend fun update(note: Note)

    // ID'ye göre tek bir notu getir
    @Query("SELECT * FROM notes WHERE id = :noteId")
    suspend fun getNoteById(noteId: Int): Note?

    // En son eklenen notu getir (Widget için)
    @Query("SELECT * FROM notes ORDER BY id DESC LIMIT 1")
    suspend fun getLatestNote(): Note?

    // Tüm notları getir (MainActivity için)
    @Query("SELECT * FROM notes ORDER BY id DESC")
    suspend fun getAllNotes(): List<Note>
}
