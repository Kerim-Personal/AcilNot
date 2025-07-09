package com.codenzi.snapnote

import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
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

    suspend fun uploadBackup(fileName: String, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val fileMetadata = File().apply {
                name = fileName
            }

            // Önce mevcut bir dosya var mı diye kontrol et
            val existingFile = findFile(fileName)
            val contentStream = ByteArrayContent("application/json", content.toByteArray())

            if (existingFile != null) {
                // Dosya varsa güncelle
                drive.files().update(existingFile.id, fileMetadata, contentStream).execute()
            } else {
                // Dosya yoksa yeni oluştur
                fileMetadata.parents = listOf(appDataFolderSpace)
                drive.files().create(fileMetadata, contentStream).setFields("id").execute()
            }
            return@withContext true
        } catch (e: IOException) {
            Log.e("GoogleDriveManager", "uploadBackup failed", e)
            return@withContext false
        }
    }

    suspend fun getBackupFiles(): List<File>? = withContext(Dispatchers.IO) {
        try {
            return@withContext drive.files().list()
                .setSpaces(appDataFolderSpace)
                .setFields(driveApiFilesFields)
                .setOrderBy("modifiedTime desc")
                .execute()
                .files
        } catch (e: IOException) {
            Log.e("GoogleDriveManager", "getBackupFiles failed", e)
            return@withContext null
        }
    }

    suspend fun downloadFile(fileId: String): String? = withContext(Dispatchers.IO) {
        try {
            val outputStream = ByteArrayOutputStream()
            drive.files().get(fileId).executeMediaAndDownloadTo(outputStream)
            return@withContext outputStream.toString("UTF-8")
        } catch (e: IOException) {
            Log.e("GoogleDriveManager", "downloadFile failed", e)
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