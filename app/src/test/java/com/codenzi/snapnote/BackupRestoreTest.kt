package com.codenzi.snapnote

import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.io.FileWriter
import java.nio.file.Files

/**
 * Test class for backup/restore functionality utilities
 */
class BackupRestoreTest {

    @Test
    fun testFileIntegrityUtilsWithTempFile() {
        // Create a temporary test file
        val testFile = Files.createTempFile("test_backup", ".txt").toFile()
        val testContent = "Test content for integrity verification"
        
        try {
            FileWriter(testFile).use { writer ->
                writer.write(testContent)
            }

            // Test SHA-256 calculation
            val fileHash = FileIntegrityUtils.calculateSHA256(testFile)
            assertNotNull("SHA-256 hash should not be null", fileHash)
            
            val contentHash = FileIntegrityUtils.calculateSHA256(testContent)
            assertNotNull("SHA-256 hash for content should not be null", contentHash)
            
            // File and content should have the same hash
            assertEquals("File and content hashes should match", fileHash, contentHash)
            
            // Verify integrity
            assertTrue("File integrity verification should pass", 
                       FileIntegrityUtils.verifyFileIntegrity(testFile, fileHash!!))
            
            assertTrue("Content integrity verification should pass",
                       FileIntegrityUtils.verifyContentIntegrity(testContent, contentHash!!))
            
            // Test with wrong hash
            assertFalse("File integrity verification should fail with wrong hash",
                        FileIntegrityUtils.verifyFileIntegrity(testFile, "wrong_hash"))
            
        } finally {
            // Cleanup
            testFile.delete()
        }
    }

    @Test
    fun testSHA256Consistency() {
        val testContent = "Consistent test content"
        
        // Calculate hash multiple times
        val hash1 = FileIntegrityUtils.calculateSHA256(testContent)
        val hash2 = FileIntegrityUtils.calculateSHA256(testContent)
        
        assertNotNull("First hash should not be null", hash1)
        assertNotNull("Second hash should not be null", hash2)
        assertEquals("Hashes should be consistent", hash1, hash2)
        
        // Test with different content
        val differentContent = "Different test content"
        val hash3 = FileIntegrityUtils.calculateSHA256(differentContent)
        
        assertNotNull("Third hash should not be null", hash3)
        assertNotEquals("Different content should have different hash", hash1, hash3)
    }

    @Test
    fun testBackupDataSerialization() {
        // Test backup data structure serialization
        val gson = com.google.gson.Gson()
        
        val testNote = Note(
            id = 1,
            title = "Test Note",
            content = "{\"text\":\"Test content\",\"checklist\":[],\"audioFilePath\":null,\"imagePath\":null}",
            createdAt = System.currentTimeMillis(),
            modifiedAt = emptyList(),
            color = "#FFECEFF1",
            isDeleted = false,
            deletedAt = null,
            showOnWidget = false
        )
        
        val appSettings = AppSettings(
            themeSelection = "system_default",
            colorSelection = "bordo",
            widgetBackgroundSelection = "widget_background"
        )
        
        val backupData = BackupData(
            settings = appSettings,
            notes = listOf(testNote),
            passwordHash = null,
            salt = null
        )
        
        // Test serialization
        val json = gson.toJson(backupData)
        assertNotNull("JSON serialization should not be null", json)
        assertTrue("JSON should contain note title", json.contains("Test Note"))
        
        // Test deserialization
        val type = object : com.google.gson.reflect.TypeToken<BackupData>() {}.type
        val deserializedData = gson.fromJson<BackupData>(json, type)
        
        assertNotNull("Deserialized data should not be null", deserializedData)
        assertEquals("Note count should match", 1, deserializedData.notes.size)
        assertEquals("Note title should match", "Test Note", deserializedData.notes[0].title)
        assertEquals("Settings should match", "bordo", deserializedData.settings.colorSelection)
    }

    @Test
    fun testGoogleDriveManagerResultClasses() {
        // Test UploadResult
        val successResult = GoogleDriveManager.UploadResult(
            success = true,
            fileId = "test_file_id",
            sha256Hash = "test_hash",
            errorMessage = null
        )
        
        assertTrue("Success result should be successful", successResult.success)
        assertEquals("File ID should match", "test_file_id", successResult.fileId)
        assertEquals("Hash should match", "test_hash", successResult.sha256Hash)
        assertNull("Error message should be null", successResult.errorMessage)
        
        val failureResult = GoogleDriveManager.UploadResult(
            success = false,
            fileId = null,
            sha256Hash = null,
            errorMessage = "Upload failed"
        )
        
        assertFalse("Failure result should not be successful", failureResult.success)
        assertNull("File ID should be null", failureResult.fileId)
        assertEquals("Error message should match", "Upload failed", failureResult.errorMessage)
        
        // Test DownloadResult
        val downloadResult = GoogleDriveManager.DownloadResult(
            success = true,
            content = "test content",
            sha256Hash = "test_hash"
        )
        
        assertTrue("Download result should be successful", downloadResult.success)
        assertEquals("Content should match", "test content", downloadResult.content)
        assertEquals("Hash should match", "test_hash", downloadResult.sha256Hash)
    }
}