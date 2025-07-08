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

class GoogleDriveManager(private val accessToken: String) {

    // Ktor HTTP istemcisini yapılandırıyoruz
    private val client = HttpClient(Android) {
        expectSuccess = true // HTTP hatalarında exception fırlat
        install(Logging) {
            level = LogLevel.ALL
        }
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true // Bilinmeyen alanları görmezden gel
            })
        }
    }

    private val DRIVE_API_FILES_URL = "https://www.googleapis.com/drive/v3/files"
    private val DRIVE_API_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"

    /**
     * Google Drive'da bir yedek dosyası arar.
     */
    private suspend fun findFile(fileName: String): DriveFile? {
        try {
            val response: DriveFileList = client.get(DRIVE_API_FILES_URL) {
                bearerAuth(accessToken)
                url {
                    parameters.append("q", "name = '$fileName'")
                    parameters.append("spaces", "appDataFolder")
                    parameters.append("fields", "files(id, name)")
                }
            }.body()
            return response.files.firstOrNull()
        } catch (e: Exception) {
            Log.e("GoogleDriveManager", "findFile failed", e)
            return null
        }
    }

    /**
     * Yedek dosyasını Drive'a yükler (yoksa oluşturur, varsa günceller).
     */
    suspend fun uploadBackup(fileName: String, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val existingFile = findFile(fileName)
            val fileId = existingFile?.id

            val metadata = """
                {
                  "name": "$fileName"
                  ${if (fileId == null) """, "parents": ["appDataFolder"]""" else ""}
                }
            """.trimIndent()

            val httpMethod = if (fileId == null) HttpMethod.Post else HttpMethod.Patch
            val url = if (fileId == null) DRIVE_API_UPLOAD_URL else "$DRIVE_API_UPLOAD_URL/$fileId"

            val response: HttpResponse = client.request(url) {
                method = httpMethod
                bearerAuth(accessToken)
                url {
                    parameters.append("uploadType", "media")
                }
                setBody(content)
                contentType(ContentType.Application.Json)
            }

            // Yükleme sonrası metadata güncellemesi (varsa)
            client.request("$DRIVE_API_FILES_URL/${response.body<DriveFile>().id}"){
                method = HttpMethod.Patch
                bearerAuth(accessToken)
                setBody(metadata)
                contentType(ContentType.Application.Json)
            }

            return@withContext response.status.isSuccess()
        } catch (e: Exception) {
            Log.e("GoogleDriveManager", "uploadBackup failed", e)
            return@withContext false
        }
    }

    // Geri yükleme için downloadFile fonksiyonu (ihtiyaç halinde) eklenebilir.
}