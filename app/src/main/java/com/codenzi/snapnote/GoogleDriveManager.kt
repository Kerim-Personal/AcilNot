package com.codenzi.snapnote

import android.util.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.encodeToString

class GoogleDriveManager(private val accessToken: String) {

    private val jsonParser = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val client = HttpClient(Android) {
        expectSuccess = false
        install(Logging) {
            level = LogLevel.BODY
        }
        install(ContentNegotiation) {
            json(jsonParser)
        }
    }

    // Değişken isimlendirme uyarısı giderildi
    private val driveApiFilesUrl = "https://www.googleapis.com/drive/v3/files"
    private val driveApiUploadUrl = "https://www.googleapis.com/upload/drive/v3/files"

    private suspend fun findFile(fileName: String): DriveFile? {
        return try {
            val response: DriveFileList = client.get(driveApiFilesUrl) {
                bearerAuth(accessToken)
                url {
                    parameters.append("q", "name = '$fileName' and 'appDataFolder' in parents")
                    parameters.append("spaces", "appDataFolder")
                    parameters.append("fields", "files(id, name)")
                }
            }.body()
            response.files.firstOrNull()
        } catch (e: Exception) {
            Log.e("GoogleDriveManager", "findFile failed", e)
            null
        }
    }

    suspend fun uploadBackup(fileName: String, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val existingFile = findFile(fileName)
            val fileId = existingFile?.id

            // DÜZELTME: Hatalı olan JSON oluşturma mantığı,
            // kotlinx.serialization'ın standart map/list kullanımıyla değiştirildi.
            val metadataContent = mutableMapOf<String, Any>(
                "name" to fileName
            )
            if (fileId == null) {
                metadataContent["parents"] = listOf("appDataFolder")
            }
            val metadataJson = jsonParser.encodeToString(metadataContent)

            // Önce metadata'yı oluştur/güncelle
            val metadataResponse = if (fileId == null) {
                client.post(driveApiFilesUrl) {
                    bearerAuth(accessToken)
                    contentType(ContentType.Application.Json)
                    setBody(metadataJson)
                    url {
                        parameters.append("fields", "id")
                    }
                }
            } else {
                client.patch("$driveApiFilesUrl/$fileId") {
                    bearerAuth(accessToken)
                    contentType(ContentType.Application.Json)
                    setBody(metadataJson)
                }
            }

            if (!metadataResponse.status.isSuccess()) {
                Log.e("GoogleDriveManager", "Metadata update failed: ${metadataResponse.bodyAsText()}")
                return@withContext false
            }

            val newFileId = metadataResponse.body<DriveFile>().id

            // Şimdi içeriği yükle
            val uploadResponse = client.patch("$driveApiUploadUrl/$newFileId") {
                bearerAuth(accessToken)
                url {
                    parameters.append("uploadType", "media")
                }
                contentType(ContentType.parse("application/json"))
                setBody(content)
            }

            return@withContext uploadResponse.status.isSuccess()

        } catch (e: Exception) {
            Log.e("GoogleDriveManager", "uploadBackup failed", e)
            return@withContext false
        }
    }

    suspend fun getBackupFiles(): List<DriveFile>? = withContext(Dispatchers.IO) {
        return@withContext try {
            val response: DriveFileList = client.get(driveApiFilesUrl) {
                bearerAuth(accessToken)
                url {
                    parameters.append("spaces", "appDataFolder")
                    parameters.append("fields", "files(id, name, modifiedTime)")
                    parameters.append("orderBy", "modifiedTime desc")
                }
            }.body()
            response.files
        } catch (e: Exception) {
            Log.e("GoogleDriveManager", "getBackupFiles failed", e)
            null
        }
    }

    suspend fun downloadFile(fileId: String): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val response: HttpResponse = client.get("$driveApiFilesUrl/$fileId") {
                bearerAuth(accessToken)
                url {
                    parameters.append("alt", "media")
                }
            }
            if(response.status.isSuccess()){
                response.bodyAsText()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("GoogleDriveManager", "downloadFile failed", e)
            null
        }
    }
}