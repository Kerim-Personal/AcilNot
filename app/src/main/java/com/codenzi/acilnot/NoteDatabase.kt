package com.codenzi.acilnot

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase


@Database(entities = [Note::class], version = 7, exportSchema = false)
@TypeConverters(Converters::class)
abstract class NoteDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: NoteDatabase? = null

        // DÜZELTME: Veritabanı sürüm 6'dan 7'ye geçerken 'showOnWidget' sütununu ekleyen
        // Migration nesnesini tanımlıyoruz. Bu, kullanıcı verilerinin korunmasını sağlar.
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 'notes' tablosuna 'showOnWidget' adında, boş olamayan (NOT NULL),
                // varsayılan değeri 0 (false) olan bir INTEGER sütunu ekliyoruz.
                // Room, Boolean tipi için INTEGER kullanır (0=false, 1=true).
                db.execSQL("ALTER TABLE notes ADD COLUMN showOnWidget INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): NoteDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NoteDatabase::class.java,
                    "note_database"
                )
                    // DÜZELTME: Veri kaybına neden olan .fallbackToDestructiveMigration() kaldırıldı.
                    // .fallbackToDestructiveMigration()

                    // DÜZELTME: Tanımladığımız Migration nesnesi veritabanına eklendi.
                    .addMigrations(MIGRATION_6_7)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}