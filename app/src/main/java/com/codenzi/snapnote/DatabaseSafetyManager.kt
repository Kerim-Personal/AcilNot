package com.codenzi.snapnote

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * GÜVENLİK YÖNETİCİSİ: Kritik veritabanı işlemlerini güvenli bir şekilde yönetir
 * Bu sınıf, yanlış veri silme işlemlerini önlemek ve kullanıcıyı bilgilendirmek için kullanılır.
 */
object DatabaseSafetyManager {

    private const val TAG = "DatabaseSafetyManager"
    
    /**
     * 30 günden eski çöp kutusundaki notları güvenli bir şekilde siler
     * @param context UI bağlamı
     * @param noteDao Veritabanı erişim nesnesi
     * @param onResult İşlem sonucu callback'i (başarılı silinen sayı, hata durumunda -1)
     */
    suspend fun safeDeleteOldTrashedNotes(
        context: Context,
        noteDao: NoteDao,
        onResult: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            // GÜVENLİK: 30 günlük timestamp'i hesapla
            val thirtyDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
            
            // GÜVENLİK: Silinecek kayıt sayısını kontrol et
            val recordsToDelete = noteDao.countOldTrashedNotes(thirtyDaysAgo)
            
            Log.i(TAG, "Found $recordsToDelete old trashed notes to delete")
            
            if (recordsToDelete == 0) {
                withContext(Dispatchers.Main) {
                    onResult(0)
                }
                return@withContext
            }
            
            // Güvenlik kontrolü: çok fazla kayıt silinmesini önle
            if (recordsToDelete > 100) {
                Log.w(TAG, "Too many records to delete: $recordsToDelete, requiring user confirmation")
                withContext(Dispatchers.Main) {
                    showDeleteConfirmationDialog(context, recordsToDelete) { confirmed ->
                        if (confirmed) {
                            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                                try {
                                    noteDao.deleteOldTrashedNotes(thirtyDaysAgo)
                                    withContext(Dispatchers.Main) {
                                        onResult(recordsToDelete)
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error deleting old trashed notes: ${e.message}", e)
                                    withContext(Dispatchers.Main) {
                                        onResult(-1)
                                    }
                                }
                            }
                        } else {
                            onResult(0)
                        }
                    }
                }
                return@withContext
            }
            
            // Normal silme işlemi
            noteDao.deleteOldTrashedNotes(thirtyDaysAgo)
            withContext(Dispatchers.Main) {
                onResult(recordsToDelete)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in safeDeleteOldTrashedNotes: ${e.message}", e)
            withContext(Dispatchers.Main) {
                onResult(-1)
            }
        }
    }
    
    /**
     * Tüm notları güvenli bir şekilde siler (geri yükleme işlemi için)
     * @param context UI bağlamı
     * @param noteDao Veritabanı erişim nesnesi
     * @param onResult İşlem sonucu callback'i (başarılı true, başarısız false)
     */
    suspend fun safeDeleteAllNotes(
        context: Context,
        noteDao: NoteDao,
        onResult: (Boolean) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            // GÜVENLİK: Toplam not sayısını kontrol et
            val totalNotes = noteDao.getTotalNotesCount()
            
            Log.i(TAG, "Total notes to delete: $totalNotes")
            
            if (totalNotes == 0) {
                withContext(Dispatchers.Main) {
                    onResult(true)
                }
                return@withContext
            }
            
            // Mutlaka kullanıcı onayı iste
            withContext(Dispatchers.Main) {
                showDeleteAllConfirmationDialog(context, totalNotes) { confirmed ->
                    if (confirmed) {
                        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                            try {
                                noteDao.deleteAllNotes()
                                Log.i(TAG, "Successfully deleted all $totalNotes notes")
                                withContext(Dispatchers.Main) {
                                    onResult(true)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error deleting all notes: ${e.message}", e)
                                withContext(Dispatchers.Main) {
                                    onResult(false)
                                }
                            }
                        }
                    } else {
                        onResult(false)
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in safeDeleteAllNotes: ${e.message}", e)
            withContext(Dispatchers.Main) {
                onResult(false)
            }
        }
    }
    
    /**
     * Çok sayıda kayıt silme onayı dialog'u
     */
    private fun showDeleteConfirmationDialog(
        context: Context,
        recordCount: Int,
        onResult: (Boolean) -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle("Çok Sayıda Kayıt Silinecek")
            .setMessage("$recordCount adet eski not kalıcı olarak silinecek. Bu işlem geri alınamaz. Devam etmek istediğinizden emin misiniz?")
            .setPositiveButton("Evet, Sil") { _, _ -> onResult(true) }
            .setNegativeButton("İptal") { _, _ -> onResult(false) }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Tüm notları silme onayı dialog'u
     */
    private fun showDeleteAllConfirmationDialog(
        context: Context,
        noteCount: Int,
        onResult: (Boolean) -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle("TÜM NOTLAR SİLİNECEK")
            .setMessage("$noteCount adet notunuzun TAMAMI kalıcı olarak silinecek. Bu işlem geri alınamaz ve veri kurtarılamaz.\n\nBu işlem genellikle geri yükleme sırasında kullanılır. Devam etmek istediğinizden emin misiniz?")
            .setPositiveButton("Evet, Tümünü Sil") { _, _ -> onResult(true) }
            .setNegativeButton("İptal") { _, _ -> onResult(false) }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Veritabanı durumu kontrolü
     * @param noteDao Veritabanı erişim nesnesi
     * @return Veritabanı durumu bilgisi
     */
    suspend fun getDatabaseStatus(noteDao: NoteDao): DatabaseStatus = withContext(Dispatchers.IO) {
        try {
            val totalNotes = noteDao.getTotalNotesCount()
            val deletedNotes = noteDao.countOldTrashedNotes(0) // Tüm silinmiş notlar
            val thirtyDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
            val oldTrashedNotes = noteDao.countOldTrashedNotes(thirtyDaysAgo)
            
            DatabaseStatus(
                totalNotes = totalNotes,
                activeNotes = totalNotes - deletedNotes,
                deletedNotes = deletedNotes,
                oldTrashedNotes = oldTrashedNotes
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting database status: ${e.message}", e)
            DatabaseStatus(0, 0, 0, 0)
        }
    }
}

/**
 * Veritabanı durumu bilgisi
 */
data class DatabaseStatus(
    val totalNotes: Int,
    val activeNotes: Int,
    val deletedNotes: Int,
    val oldTrashedNotes: Int
)