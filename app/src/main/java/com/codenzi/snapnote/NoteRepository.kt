package com.codenzi.snapnote

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepository @Inject constructor(private val noteDao: NoteDao) {

    suspend fun getNoteById(noteId: Int): Note? = noteDao.getNoteById(noteId)

    suspend fun insert(note: Note): Long = noteDao.insert(note)

    suspend fun update(note: Note) = noteDao.update(note)

    suspend fun softDeleteById(noteId: Int, timestamp: Long) = noteDao.softDeleteById(noteId, timestamp)

    /**
     * Güvenli silme metodu: 30 günden eski çöp kutusundaki notları siler
     * Doğrulama mekanizması ile yanlış verilerin silinmesini önler
     */
    suspend fun deleteOldTrashedNotesSafely(thirtyDaysAgoTimestamp: Long): Int {
        try {
            // Önce silinecek not sayısını kontrol et
            val count = noteDao.countOldTrashedNotes(thirtyDaysAgoTimestamp)
            
            // Güvenlik kontrolü: Çok fazla not silinmesin
            if (count > 1000) {
                Log.w("NoteRepository", "Çok fazla not silinmeye çalışılıyor: $count. Güvenlik için işlem iptal edildi.")
                throw IllegalStateException("Çok fazla not silinmeye çalışılıyor: $count. Güvenlik için işlem iptal edildi.")
            }
            
            // Doğrulama: Sadece çöp kutusundaki notları sil
            val notesToDelete = noteDao.getOldTrashedNotes(thirtyDaysAgoTimestamp)
            val nonTrashedNotes = notesToDelete.filter { !it.isDeleted }
            if (nonTrashedNotes.isNotEmpty()) {
                Log.e("NoteRepository", "Çöp kutusunda olmayan ${nonTrashedNotes.size} not silinmeye çalışılıyor. İşlem iptal edildi.")
                throw IllegalStateException("Çöp kutusunda olmayan notlar silinmeye çalışılıyor. İşlem iptal edildi.")
            }
            
            // Güvenli silme işlemini gerçekleştir
            noteDao.deleteOldTrashedNotes(thirtyDaysAgoTimestamp)
            Log.i("NoteRepository", "Çöp kutusundan $count eski not başarıyla silindi.")
            return count
        } catch (e: Exception) {
            Log.e("NoteRepository", "Eski notları silme işleminde hata: ${e.message}", e)
            throw e
        }
    }

    /**
     * Güvenli silme metodu: Tüm notları siler - açık onay gerektirir
     * Kullanıcıdan işlem öncesinde açık bir şekilde onay alınmasını sağlar
     */
    suspend fun deleteAllNotesSafely(confirmationToken: String): Int {
        if (confirmationToken != "CONFIRM_DELETE_ALL_NOTES") {
            Log.w("NoteRepository", "Tüm notları silme işlemi için geçersiz onay token'ı.")
            throw IllegalArgumentException("Tüm notları silmek için geçerli onay token'ı gerekli.")
        }
        
        try {
            val count = noteDao.getAllNotesCount()
            
            // Güvenlik kontrolü: çok fazla not varsa uyar
            if (count > 500) {
                Log.w("NoteRepository", "Çok fazla not ($count) silmeye çalışılıyor. Güvenlik kontrolü gerekli.")
                throw IllegalStateException("Çok fazla not ($count) silmeye çalışılıyor. Güvenlik kontrolü gerekli.")
            }
            
            noteDao.deleteAllNotes()
            Log.i("NoteRepository", "Tüm notlar ($count adet) başarıyla silindi.")
            return count
        } catch (e: Exception) {
            Log.e("NoteRepository", "Tüm notları silme işleminde hata: ${e.message}", e)
            throw e
        }
    }

    // İleride ihtiyaç duyabileceğiniz diğer Dao fonksiyonlarını buraya ekleyebilirsiniz.
}