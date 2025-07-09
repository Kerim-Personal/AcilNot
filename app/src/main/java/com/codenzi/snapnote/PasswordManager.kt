package com.codenzi.snapnote

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Uygulama parolalarını güvenli bir şekilde yönetmek için yardımcı sınıf.
 * Parolalar, SHA-256 ile hash'lenip tuzlandıktan sonra,
 * anahtar ve değerleri şifrelenmiş olan EncryptedSharedPreferences'ta saklanır.
 */
object PasswordManager {

    private const val PREFS_NAME = "AppSecurityPrefs"
    private const val KEY_PASSWORD_HASH = "password_hash"
    private const val KEY_SALT = "salt"
    private const val KEY_IS_PASSWORD_ENABLED = "is_password_enabled"

    private fun getSharedPreferences(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun hashPassword(password: String, salt: ByteArray): Pair<String, ByteArray> {
        val passwordBytes = password.toByteArray(Charsets.UTF_8)
        val combinedBytes = salt + passwordBytes

        val digest = MessageDigest.getInstance("SHA-256")
        val hashedBytes = digest.digest(combinedBytes)

        return Base64.encodeToString(hashedBytes, Base64.NO_WRAP) to salt
    }

    private fun generateSalt(): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return salt
    }

    fun setPassword(context: Context, newPassword: String) {
        val salt = generateSalt()
        val (hashedPassword, _) = hashPassword(newPassword, salt)
        getSharedPreferences(context).edit {
            putString(KEY_PASSWORD_HASH, hashedPassword)
            putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            putBoolean(KEY_IS_PASSWORD_ENABLED, true)
        }
    }

    fun checkPassword(context: Context, enteredPassword: String): Boolean {
        val prefs = getSharedPreferences(context)
        val storedHash = prefs.getString(KEY_PASSWORD_HASH, null)
        val storedSaltString = prefs.getString(KEY_SALT, null)

        if (storedHash == null || storedSaltString == null) {
            return false
        }

        val storedSalt = Base64.decode(storedSaltString, Base64.NO_WRAP)
        val (enteredPasswordHashed, _) = hashPassword(enteredPassword, storedSalt)

        return storedHash == enteredPasswordHashed
    }

    fun checkPassword(enteredPassword: String, saltBase64: String, hash: String): Boolean {
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val (enteredPasswordHashed, _) = hashPassword(enteredPassword, salt)
        return hash == enteredPasswordHashed
    }

    fun isPasswordSet(context: Context): Boolean {
        return getSharedPreferences(context).getBoolean(KEY_IS_PASSWORD_ENABLED, false) &&
                getSharedPreferences(context).getString(KEY_PASSWORD_HASH, null) != null
    }

    fun disablePassword(context: Context) {
        getSharedPreferences(context).edit {
            remove(KEY_PASSWORD_HASH)
            remove(KEY_SALT)
            putBoolean(KEY_IS_PASSWORD_ENABLED, false)
        }
    }

    fun getPasswordHash(context: Context): String? {
        return getSharedPreferences(context).getString(KEY_PASSWORD_HASH, null)
    }

    fun getSalt(context: Context): String? {
        return getSharedPreferences(context).getString(KEY_SALT, null)
    }

    /**
     * YENİ: Yedekten geri yüklenen parola bilgilerini güvenli bir şekilde kaydeder.
     * Bu fonksiyon, `getSharedPreferences` hatasını çözmek için eklenmiştir.
     */
    fun restorePassword(context: Context, hash: String, salt: String) {
        getSharedPreferences(context).edit {
            putString(KEY_PASSWORD_HASH, hash)
            putString(KEY_SALT, salt)
            putBoolean(KEY_IS_PASSWORD_ENABLED, true)
        }
    }
}