// kerim-personal/acilnot/AcilNot-834706bd32961a54e3924bd58580b2d85464274f/app/src/main/java/com/codenzi/snapnote/GoogleDriveManager.kt

package com.codenzi.snapnote

import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class GoogleDriveManager(private val credential: GoogleAccountCredential) {

    private val drive: Drive by lazy {
        Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("SnapNote")
         .build()
    }

    private val driveApiFilesFields = "files(id, name, modifiedTime)"
    private val appDataFolderSpace = "appDataFolder"
    
    companion object {
        private const val TAG = "GoogleDriveManager"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 2000L
    }

    /**
     * Data class to hold upload result with integrity information
     */
    data class UploadResult(
        val success: Boolean,
        val fileId: String? = null,
        val sha256Hash: String? = null,
        val errorMessage: String? = null
    )

    /**
     * Data class to hold download result with integrity information
     */
    data class DownloadResult(
        val success: Boolean,
        val content: String? = null,
        val sha256Hash: String? = null,
        val errorMessage: String? = null
    )

    /**
     * Uploads JSON backup with retry mechanism and integrity verification
     */
    suspend fun uploadJsonBackupWithIntegrity(fileName: String, content: String): UploadResult = withContext(Dispatchers.IO) {
        val contentHash = FileIntegrityUtils.calculateSHA256(content)
        if (contentHash == null) {
            return@withContext UploadResult(false, errorMessage = "Failed to calculate content hash")
        }

        repeat(MAX_RETRY_ATTEMPTS) { attempt ->
            try {
                val fileMetadata = File().apply {
                    name = fileName
                    description = "SHA256:$contentHash" // Store hash in file description
                }
                
                val existingFile = findFile(fileName)
                val contentStream = ByteArrayContent("application/json", content.toByteArray())

                val uploadedFile = if (existingFile != null) {
                    drive.files().update(existingFile.id, fileMetadata, contentStream).execute()
                } else {
                    fileMetadata.parents = listOf(appDataFolderSpace)
                    drive.files().create(fileMetadata, contentStream).setFields("id").execute()
                }

                Log.i(TAG, "JSON backup uploaded successfully with hash: $contentHash")
                return@withContext UploadResult(true, uploadedFile.id, contentHash)
                
            } catch (e: IOException) {
                Log.w(TAG, "Upload attempt ${attempt + 1} failed", e)
                if (attempt == MAX_RETRY_ATTEMPTS - 1) {
                    Log.e(TAG, "uploadJsonBackupWithIntegrity failed after $MAX_RETRY_ATTEMPTS attempts", e)
                    return@withContext UploadResult(false, errorMessage = e.message)
                }
                kotlinx.coroutines.delay(RETRY_DELAY_MS)
            }
        }
        return@withContext UploadResult(false, errorMessage = "Max retry attempts exceeded")
    }

    /**
     * Downloads JSON backup with integrity verification
     */
    suspend fun downloadJsonBackupWithIntegrity(fileId: String): DownloadResult = withContext(Dispatchers.IO) {
        repeat(MAX_RETRY_ATTEMPTS) { attempt ->
            try {
                // First get file metadata to extract hash
                val fileMetadata = drive.files().get(fileId).setFields("description").execute()
                val expectedHash = fileMetadata.description?.let { desc ->
                    if (desc.startsWith("SHA256:")) desc.substring(7) else null
                }

                val outputStream = ByteArrayOutputStream()
                drive.files().get(fileId).executeMediaAndDownloadTo(outputStream)
                val content = outputStream.toString("UTF-8")

                // Verify integrity if hash is available
                if (expectedHash != null) {
                    if (!FileIntegrityUtils.verifyContentIntegrity(content, expectedHash)) {
                        Log.e(TAG, "Downloaded backup file integrity check failed")
                        return@withContext DownloadResult(false, errorMessage = "File integrity verification failed")
                    }
                    Log.i(TAG, "Downloaded backup file integrity verified successfully")
                }

                return@withContext DownloadResult(true, content, expectedHash)
                
            } catch (e: IOException) {
                Log.w(TAG, "Download attempt ${attempt + 1} failed", e)
                if (attempt == MAX_RETRY_ATTEMPTS - 1) {
                    Log.e(TAG, "downloadJsonBackupWithIntegrity failed after $MAX_RETRY_ATTEMPTS attempts", e)
                    return@withContext DownloadResult(false, errorMessage = e.message)
                }
                kotlinx.coroutines.delay(RETRY_DELAY_MS)
            }
        }
        return@withContext DownloadResult(false, errorMessage = "Max retry attempts exceeded")
    }

    private suspend fun findFile(fileName: String): File? = withContext(Dispatchers.IO) {
        try {
            val result = drive.files().list()
                .setSpaces(appDataFolderSpace)
                .setQ("name = '$fileName'")
                .setFields("files(id, name)")
                .execute()
            return@withContext result.files.firstOrNull()
        } catch (e: IOException) {
            Log.e("GoogleDriveManager", "findFile failed", e)
            return@withContext null
        }
    }

    suspend fun getBackupFiles(): List<File>? = withContext(Dispatchers.IO) {
        try {
            return@withContext drive.files().list()
                .setSpaces(appDataFolderSpace)
                .setFields(driveApiFilesFields)
                .setQ("name = 'snapnote_backup.json'") // Sadece ana yedek dosyasını bul
                .setOrderBy("modifiedTime desc")
                .execute()
                .files
        } catch (e: IOException) {
            Log.e("GoogleDriveManager", "getBackupFiles failed", e)
            return@withContext null
        }
    }

    /**
     * Uploads media file with integrity verification and retry mechanism
     */
    suspend fun uploadMediaFileWithIntegrity(localFile: java.io.File, mimeType: String): UploadResult = withContext(Dispatchers.IO) {
        if (!localFile.exists() || !localFile.canRead()) {
            return@withContext UploadResult(false, errorMessage = "Local file does not exist or cannot be read")
        }

        val fileHash = FileIntegrityUtils.calculateSHA256(localFile)
        if (fileHash == null) {
            return@withContext UploadResult(false, errorMessage = "Failed to calculate file hash")
        }

        repeat(MAX_RETRY_ATTEMPTS) { attempt ->
            try {
                val fileMetadata = File().apply {
                    name = localFile.name
                    parents = listOf(appDataFolderSpace)
                    description = "SHA256:$fileHash" // Store hash in file description
                }
                
                val mediaContent = FileContent(mimeType, localFile)
                val file = drive.files().create(fileMetadata, mediaContent).setFields("id").execute()
                
                Log.i(TAG, "Media file uploaded successfully: ${localFile.name} with hash: $fileHash")
                return@withContext UploadResult(true, file.id, fileHash)
                
            } catch (e: IOException) {
                Log.w(TAG, "Upload attempt ${attempt + 1} failed for ${localFile.name}", e)
                if (attempt == MAX_RETRY_ATTEMPTS - 1) {
                    Log.e(TAG, "uploadMediaFileWithIntegrity failed for ${localFile.name} after $MAX_RETRY_ATTEMPTS attempts", e)
                    return@withContext UploadResult(false, errorMessage = e.message)
                }
                kotlinx.coroutines.delay(RETRY_DELAY_MS)
            }
        }
        return@withContext UploadResult(false, errorMessage = "Max retry attempts exceeded")
    }

    /**
     * Downloads media file with integrity verification and retry mechanism
     */
    suspend fun downloadMediaFileWithIntegrity(fileId: String, destinationFile: java.io.File): DownloadResult = withContext(Dispatchers.IO) {
        repeat(MAX_RETRY_ATTEMPTS) { attempt ->
            try {
                // First get file metadata to extract expected hash
                val fileMetadata = drive.files().get(fileId).setFields("description").execute()
                val expectedHash = fileMetadata.description?.let { desc ->
                    if (desc.startsWith("SHA256:")) desc.substring(7) else null
                }

                val outputStream = FileOutputStream(destinationFile)
                drive.files().get(fileId).executeMediaAndDownloadTo(outputStream)
                outputStream.close()

                if (destinationFile.length() == 0L) {
                    destinationFile.delete()
                    return@withContext DownloadResult(false, errorMessage = "Downloaded file is empty")
                }

                // Verify integrity if hash is available
                if (expectedHash != null) {
                    if (!FileIntegrityUtils.verifyFileIntegrity(destinationFile, expectedHash)) {
                        destinationFile.delete()
                        Log.e(TAG, "Downloaded media file integrity check failed for $fileId")
                        return@withContext DownloadResult(false, errorMessage = "File integrity verification failed")
                    }
                    Log.i(TAG, "Downloaded media file integrity verified successfully: $fileId")
                }

                return@withContext DownloadResult(true, sha256Hash = expectedHash)
                
            } catch (e: IOException) {
                Log.w(TAG, "Download attempt ${attempt + 1} failed for file $fileId", e)
                destinationFile.delete() // Clean up partial download
                if (attempt == MAX_RETRY_ATTEMPTS - 1) {
                    Log.e(TAG, "downloadMediaFileWithIntegrity failed for id $fileId after $MAX_RETRY_ATTEMPTS attempts", e)
                    return@withContext DownloadResult(false, errorMessage = e.message)
                }
                kotlinx.coroutines.delay(RETRY_DELAY_MS)
            }
        }
        return@withContext DownloadResult(false, errorMessage = "Max retry attempts exceeded")
    }

    /**
     * YENİ: Drive'dan belirtilen dosyayı bulur ve siler.
     * @param fileName Silinecek dosyanın adı (örn: "snapnote_credentials.json")
     * @return İşlem başarılıysa true, değilse false.
     */
    suspend fun deleteFile(fileName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val fileToDelete = findFile(fileName)
            if (fileToDelete != null) {
                drive.files().delete(fileToDelete.id).execute()
                return@withContext true
            }
            // Dosya zaten yoksa, işlemi başarılı kabul et.
            return@withContext true
        } catch (e: IOException) {
            Log.e(TAG, "deleteFile failed for $fileName", e)
            return@withContext false
        }
    }

    /**
     * Deletes a file by its Drive file ID
     * @param fileId The Drive file ID to delete
     * @return true if successful, false otherwise
     */
    suspend fun deleteFileById(fileId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            drive.files().delete(fileId).execute()
            Log.i(TAG, "Successfully deleted file with ID: $fileId")
            return@withContext true
        } catch (e: IOException) {
            Log.e(TAG, "deleteFileById failed for $fileId", e)
            return@withContext false
        }
    }

    // Legacy methods for backward compatibility
    suspend fun uploadJsonBackup(fileName: String, content: String): Boolean = 
        uploadJsonBackupWithIntegrity(fileName, content).success

    suspend fun downloadJsonBackup(fileId: String): String? = 
        downloadJsonBackupWithIntegrity(fileId).content

    suspend fun uploadMediaFile(localFile: java.io.File, mimeType: String): String? = 
        uploadMediaFileWithIntegrity(localFile, mimeType).fileId

    suspend fun downloadMediaFile(fileId: String, destinationFile: java.io.File): Boolean = 
        downloadMediaFileWithIntegrity(fileId, destinationFile).success
}