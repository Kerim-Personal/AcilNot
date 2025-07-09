// kerim-personal/acilnot/AcilNot-67f040771546d8d1c2779533e2d914d9dbbd06cc/app/src/main/java/com/codenzi/snapnote/GoogleDriveManager.kt

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

class GoogleDriveManager(private val credential: GoogleAccountCredential) {

    private val drive: Drive by lazy {
        Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("SnapNote").build()
    }

    private val driveApiFilesFields = "files(id, name, modifiedTime)"
    private val appDataFolderSpace = "appDataFolder"

    // --- YENİ FONKSİYON: Medya dosyalarını Drive'a yükler ---
    suspend fun uploadMediaFile(localFile: java.io.File, mimeType: String): String? = withContext(Dispatchers.IO) {
        try {
            val fileMetadata = File().apply {
                name = localFile.name
                parents = listOf(appDataFolderSpace)
            }
            val mediaContent = FileContent(mimeType, localFile)
            val file = drive.files().create(fileMetadata, mediaContent).setFields("id").execute()
            return@withContext file.id
        } catch (e: IOException) {
            Log.e("GoogleDriveManager", "uploadMediaFile failed for ${localFile.name}", e)
            return@withContext null
        }
    }

    // --- MEVCUT FONKSİYON: Adını daha anlaşılır yapalım ---
    suspend fun uploadJsonBackup(fileName: String, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val fileMetadata = File().apply {
                name = fileName
            }
            val existingFile = findFile(fileName)
            val contentStream = ByteArrayContent("application/json", content.toByteArray())

            if (existingFile != null) {
                drive.files().update(existingFile.id, fileMetadata, contentStream).execute()
            } else {
                fileMetadata.parents = listOf(appDataFolderSpace)
                drive.files().create(fileMetadata, contentStream).setFields("id").execute()
            }
            return@withContext true
        } catch (e: IOException) {
            Log.e("GoogleDriveManager", "uploadJsonBackup failed", e)
            return@withContext false
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

    // --- YENİ FONKSİYON: Medya dosyalarını ID ile Drive'dan indirir ---
    suspend fun downloadMediaFile(fileId: String, destinationFile: java.io.File): Boolean = withContext(Dispatchers.IO) {
        try {
            val outputStream = FileOutputStream(destinationFile)
            drive.files().get(fileId).executeMediaAndDownloadTo(outputStream)
            outputStream.close()
            return@withContext true
        } catch (e: IOException) {
            Log.e("GoogleDriveManager", "downloadMediaFile failed for id $fileId", e)
            destinationFile.delete() // Başarısız olursa yarım dosyayı sil
            return@withContext false
        }
    }


    suspend fun downloadJsonBackup(fileId: String): String? = withContext(Dispatchers.IO) {
        try {
            val outputStream = ByteArrayOutputStream()
            drive.files().get(fileId).executeMediaAndDownloadTo(outputStream)
            return@withContext outputStream.toString("UTF-8")
        } catch (e: IOException) {
            Log.e("GoogleDriveManager", "downloadJsonBackup failed", e)
            return@withContext null
        }
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
}