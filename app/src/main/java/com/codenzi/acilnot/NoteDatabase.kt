package com.codenzi.acilnot

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

// VERSIYONU 3'E YÜKSELTİN
@Database(entities = [Note::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class NoteDatabase : RoomDatabase() {

    // ... (geri kalan kod aynı) ...
// ... (Mevcut kodunuz burada) ...

    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: NoteDatabase? = null

        fun getDatabase(context: Context): NoteDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NoteDatabase::class.java,
                    "note_database"
                )
                    // Migration'ı kaldırıyoruz.
                    .fallbackToDestructiveMigration() // Şema değişirse veritabanını yeniden oluşturur.
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}