package com.codenzi.snapnote

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Veritabanı versiyonunun 8 olduğundan emin olun
@Database(entities = [Note::class], version = 8, exportSchema = false)
@TypeConverters(Converters::class)
abstract class NoteDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: NoteDatabase? = null

        // Bu migration, versiyon 6'dan 7'ye geçiş içindi, bu kalmalı.
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN showOnWidget INTEGER NOT NULL DEFAULT 0")
            }
        }

        // BU, YAZMANIZ GEREKEN YENİ VE KRİTİK MIGRATION PLANIDIR
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Veritabanına yeni Index'i eklemesi için SQL komutu
                db.execSQL("CREATE INDEX `index_notes_isDeleted_showOnWidget` ON `notes` (`isDeleted`, `showOnWidget`)")
            }
        }

        fun getDatabase(context: Context): NoteDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NoteDatabase::class.java,
                    "note_database"
                )
                    // Room'a hem eski hem de yeni taşıma planını veriyoruz.
                    // O, hangi kullanıcının hangi versiyonda olduğuna bakıp doğru olanı seçecektir.
                    .addMigrations(MIGRATION_6_7, MIGRATION_7_8)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}