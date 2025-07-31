package com.codenzi.snapnote

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * File integrity verification utilities for backup/restore operations
 */
object FileIntegrityUtils {
    
    private const val TAG = "FileIntegrityUtils"
    
    /**
     * Calculates SHA-256 hash of a file
     * @param file The file to calculate hash for
     * @return SHA-256 hash as hex string, or null if calculation fails
     */
    fun calculateSHA256(file: File): String? {
        if (!file.exists() || !file.canRead()) {
            Log.e(TAG, "File does not exist or cannot be read: ${file.absolutePath}")
            return null
        }
        
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate SHA-256 for file: ${file.absolutePath}", e)
            null
        }
    }
    
    /**
     * Calculates SHA-256 hash of a string
     * @param content The string content to hash
     * @return SHA-256 hash as hex string, or null if calculation fails
     */
    fun calculateSHA256(content: String): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(content.toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate SHA-256 for string content", e)
            null
        }
    }
    
    /**
     * Verifies if a file matches the expected SHA-256 hash
     * @param file The file to verify
     * @param expectedHash The expected SHA-256 hash
     * @return true if hashes match, false otherwise
     */
    fun verifyFileIntegrity(file: File, expectedHash: String): Boolean {
        val actualHash = calculateSHA256(file)
        if (actualHash == null) {
            Log.e(TAG, "Could not calculate hash for verification: ${file.absolutePath}")
            return false
        }
        
        val isValid = actualHash.equals(expectedHash, ignoreCase = true)
        if (!isValid) {
            Log.w(TAG, "File integrity check failed for ${file.absolutePath}. Expected: $expectedHash, Actual: $actualHash")
        }
        
        return isValid
    }
    
    /**
     * Verifies if string content matches the expected SHA-256 hash
     * @param content The content to verify
     * @param expectedHash The expected SHA-256 hash
     * @return true if hashes match, false otherwise
     */
    fun verifyContentIntegrity(content: String, expectedHash: String): Boolean {
        val actualHash = calculateSHA256(content)
        if (actualHash == null) {
            Log.e(TAG, "Could not calculate hash for content verification")
            return false
        }
        
        val isValid = actualHash.equals(expectedHash, ignoreCase = true)
        if (!isValid) {
            Log.w(TAG, "Content integrity check failed. Expected: $expectedHash, Actual: $actualHash")
        }
        
        return isValid
    }
}