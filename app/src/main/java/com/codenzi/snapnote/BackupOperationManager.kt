package com.codenzi.snapnote

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * YEDEKLEME YÖNETİCİSİ: Google Drive yedekleme işlemlerini güvenli ve geri bildirimli yönetir
 */
class BackupOperationManager(
    private val context: Context,
    private val googleDriveManager: GoogleDriveManager
) {
    
    private val tag = "BackupOperationManager"
    
    /**
     * Yedekleme işlem sonucu
     */
    data class BackupResult(
        val success: Boolean,
        val message: String,
        val errorType: BackupErrorType? = null,
        val retryable: Boolean = false
    )
    
    enum class BackupErrorType {
        NETWORK_ERROR,
        AUTHENTICATION_ERROR,
        STORAGE_ERROR,
        DATA_ERROR,
        TIMEOUT_ERROR,
        UNKNOWN_ERROR
    }
    
    /**
     * İlerleme callback tipi
     */
    typealias ProgressCallback = (progress: Int, message: String) -> Unit
    
    /**
     * Gelişmiş yedekleme işlemi
     * @param fileName Yedek dosya adı
     * @param content Yedeklenecek içerik
     * @param progressCallback İlerleme callback'i
     * @param maxRetries Maksimum deneme sayısı
     * @return Yedekleme sonucu
     */
    suspend fun performBackupWithFeedback(
        fileName: String,
        content: String,
        progressCallback: ProgressCallback,
        maxRetries: Int = 3
    ): BackupResult = withContext(Dispatchers.IO) {
        
        var lastError: Exception? = null
        
        repeat(maxRetries) { attempt ->
            try {
                progressCallback(10, "Yedekleme başlatılıyor... (Deneme ${attempt + 1}/$maxRetries)")
                
                // GÜVENLİK: İçerik kontrolü
                if (content.isBlank()) {
                    return@withContext BackupResult(
                        success = false,
                        message = "Yedeklenecek veri boş",
                        errorType = BackupErrorType.DATA_ERROR,
                        retryable = false
                    )
                }
                
                progressCallback(25, "Dosya boyutu kontrol ediliyor...")
                
                // Dosya boyutu kontrolü (50MB limit)
                val contentSizeInMB = content.toByteArray().size / (1024 * 1024)
                if (contentSizeInMB > 50) {
                    return@withContext BackupResult(
                        success = false,
                        message = "Yedek dosyası çok büyük: ${contentSizeInMB}MB (Maksimum: 50MB)",
                        errorType = BackupErrorType.DATA_ERROR,
                        retryable = false
                    )
                }
                
                progressCallback(50, "Google Drive'a bağlanılıyor...")
                
                // Timeout ile yedekleme işlemi
                val success = withTimeoutOrNull(60000L) { // 60 saniye timeout
                    googleDriveManager.uploadJsonBackup(fileName, content)
                }
                
                when {
                    success == null -> {
                        throw Exception("Yedekleme işlemi zaman aşımına uğradı")
                    }
                    success == true -> {
                        progressCallback(100, "Yedekleme başarıyla tamamlandı")
                        return@withContext BackupResult(
                            success = true,
                            message = "Yedekleme başarıyla tamamlandı"
                        )
                    }
                    else -> {
                        throw Exception("Yedekleme işlemi başarısız")
                    }
                }
                
            } catch (e: Exception) {
                lastError = e
                Log.w(tag, "Backup attempt ${attempt + 1} failed: ${e.message}", e)
                
                if (attempt < maxRetries - 1) {
                    progressCallback(
                        30 + (attempt * 20),
                        "Yedekleme başarısız, tekrar deneniyor... (${e.message})"
                    )
                    delay(2000) // 2 saniye bekle
                }
            }
        }
        
        // Tüm denemeler başarısız
        val errorType = categorizeError(lastError)
        BackupResult(
            success = false,
            message = "Yedekleme ${maxRetries} deneme sonunda başarısız: ${lastError?.message}",
            errorType = errorType,
            retryable = errorType != BackupErrorType.DATA_ERROR && errorType != BackupErrorType.AUTHENTICATION_ERROR
        )
    }
    
    /**
     * Dosya silme işlemi gelişmiş geri bildirimle
     */
    suspend fun deleteFileWithFeedback(
        fileName: String,
        progressCallback: ProgressCallback
    ): BackupResult = withContext(Dispatchers.IO) {
        
        try {
            progressCallback(25, "Dosya aranıyor...")
            
            if (fileName.isBlank()) {
                return@withContext BackupResult(
                    success = false,
                    message = "Dosya adı boş",
                    errorType = BackupErrorType.DATA_ERROR,
                    retryable = false
                )
            }
            
            progressCallback(50, "Silme işlemi başlatılıyor...")
            
            val success = withTimeoutOrNull(30000L) { // 30 saniye timeout
                googleDriveManager.deleteFile(fileName)
            }
            
            when {
                success == null -> {
                    BackupResult(
                        success = false,
                        message = "Silme işlemi zaman aşımına uğradı",
                        errorType = BackupErrorType.TIMEOUT_ERROR,
                        retryable = true
                    )
                }
                success == true -> {
                    progressCallback(100, "Dosya başarıyla silindi")
                    BackupResult(
                        success = true,
                        message = "Dosya başarıyla silindi"
                    )
                }
                else -> {
                    BackupResult(
                        success = false,
                        message = "Dosya silme işlemi başarısız",
                        errorType = BackupErrorType.STORAGE_ERROR,
                        retryable = true
                    )
                }
            }
            
        } catch (e: Exception) {
            Log.e(tag, "Delete file failed: ${e.message}", e)
            BackupResult(
                success = false,
                message = "Dosya silme hatası: ${e.message}",
                errorType = categorizeError(e),
                retryable = true
            )
        }
    }
    
    /**
     * Medya dosyası yükleme işlemi
     */
    suspend fun uploadMediaFileWithFeedback(
        localFile: java.io.File,
        mimeType: String,
        progressCallback: ProgressCallback,
        maxRetries: Int = 3
    ): BackupResult = withContext(Dispatchers.IO) {
        
        try {
            progressCallback(10, "Dosya kontrol ediliyor...")
            
            if (!localFile.exists()) {
                return@withContext BackupResult(
                    success = false,
                    message = "Dosya bulunamadı: ${localFile.absolutePath}",
                    errorType = BackupErrorType.DATA_ERROR,
                    retryable = false
                )
            }
            
            val fileSizeInMB = localFile.length() / (1024 * 1024)
            if (fileSizeInMB > 100) {
                return@withContext BackupResult(
                    success = false,
                    message = "Dosya çok büyük: ${fileSizeInMB}MB (Maksimum: 100MB)",
                    errorType = BackupErrorType.DATA_ERROR,
                    retryable = false
                )
            }
            
            progressCallback(30, "Yükleme başlatılıyor...")
            
            val result = googleDriveManager.uploadMediaFileWithRetry(localFile, mimeType, maxRetries)
            
            if (result != null) {
                progressCallback(100, "Medya dosyası başarıyla yüklendi")
                BackupResult(
                    success = true,
                    message = "Medya dosyası başarıyla yüklendi. Drive ID: $result"
                )
            } else {
                BackupResult(
                    success = false,
                    message = "Medya dosyası yükleme başarısız",
                    errorType = BackupErrorType.STORAGE_ERROR,
                    retryable = true
                )
            }
            
        } catch (e: Exception) {
            Log.e(tag, "Upload media file failed: ${e.message}", e)
            BackupResult(
                success = false,
                message = "Medya dosyası yükleme hatası: ${e.message}",
                errorType = categorizeError(e),
                retryable = true
            )
        }
    }
    
    /**
     * Hata türünü kategorize et
     */
    private fun categorizeError(error: Exception?): BackupErrorType {
        return when {
            error == null -> BackupErrorType.UNKNOWN_ERROR
            error.message?.contains("network", ignoreCase = true) == true -> BackupErrorType.NETWORK_ERROR
            error.message?.contains("timeout", ignoreCase = true) == true -> BackupErrorType.TIMEOUT_ERROR
            error.message?.contains("auth", ignoreCase = true) == true -> BackupErrorType.AUTHENTICATION_ERROR
            error.message?.contains("storage", ignoreCase = true) == true -> BackupErrorType.STORAGE_ERROR
            error.message?.contains("data", ignoreCase = true) == true -> BackupErrorType.DATA_ERROR
            else -> BackupErrorType.UNKNOWN_ERROR
        }
    }
    
    /**
     * Yedekleme durumu kontrolü
     */
    suspend fun checkBackupStatus(): BackupResult = withContext(Dispatchers.IO) {
        try {
            val backupFiles = googleDriveManager.getBackupFiles()
            when {
                backupFiles == null -> BackupResult(
                    success = false,
                    message = "Yedekleme durumu kontrol edilemedi",
                    errorType = BackupErrorType.NETWORK_ERROR,
                    retryable = true
                )
                backupFiles.isEmpty() -> BackupResult(
                    success = true,
                    message = "Hiç yedekleme dosyası bulunamadı"
                )
                else -> BackupResult(
                    success = true,
                    message = "${backupFiles.size} adet yedekleme dosyası bulundu"
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Check backup status failed: ${e.message}", e)
            BackupResult(
                success = false,
                message = "Yedekleme durumu kontrol hatası: ${e.message}",
                errorType = categorizeError(e),
                retryable = true
            )
        }
    }
}