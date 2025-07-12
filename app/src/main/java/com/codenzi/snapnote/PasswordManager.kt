// kerim-personal/acilnot/AcilNot-90a5b80a56420cb5716c86163cb8b3609f8218b8/app/src/main/java/com/codenzi/snapnote/PasswordManager.kt

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
 * Bu sınıf, MyApplication'da oluşturulan tek bir EncryptedSharedPreferences örneğini kullanır.
 * Bu, hem "deprecated" uyarılarını çözer hem de çökme riskini ortadan kaldırır.
 */
object PasswordManager {

    private const val PREFS_NAME = "AppSecurityPrefs"
    private const val KEY_PASSWORD_HASH = "password_hash"
    private const val KEY_SALT = "salt"
    private const val KEY_IS_PASSWORD_ENABLED = "is_password_enabled"

    @Volatile
    private var encryptedPrefsInstance: SharedPreferences? = null

    // Sadece MyApplication tarafından çağrılacak olan başlatma metodu.
    fun initialize(context: Context) {
        if (encryptedPrefsInstance == null) {
            synchronized(this) {
                if (encryptedPrefsInstance == null) {
                    val masterKey = MasterKey.Builder(context.applicationContext)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()

                    encryptedPrefsInstance = EncryptedSharedPreferences.create(
                        context.applicationContext,
                        PREFS_NAME,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                }
            }
        }
    }

    // Güvenli SharedPreferences örneğini döndüren metod.
    // Bu metod çağrılmadan önce initialize'ın çağrılmış olması gerekir.
    private fun getSharedPreferences(): SharedPreferences {
        return encryptedPrefsInstance ?: throw IllegalStateException(
            "PasswordManager must be initialized in Application.onCreate()"
        )
    }

    private fun hashPassword(password: String, salt: ByteArray): String {
        val passwordBytes = password.toByteArray(Charsets.UTF_8)
        val combinedBytes = salt + passwordBytes
        val digest = MessageDigest.getInstance("SHA-256")
        val hashedBytes = digest.digest(combinedBytes)
        return Base64.encodeToString(hashedBytes, Base64.NO_WRAP)
    }

    private fun generateSalt(): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return salt
    }

    fun setPassword(context: Context, newPassword: String) {
        val salt = generateSalt()
        val hashedPassword = hashPassword(newPassword, salt)
        getSharedPreferences().edit(commit = true) {
            putString(KEY_PASSWORD_HASH, hashedPassword)
            putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            putBoolean(KEY_IS_PASSWORD_ENABLED, true)
        }
    }

    fun checkPassword(context: Context, enteredPassword: String): Boolean {
        val prefs = getSharedPreferences()
        val storedHash = prefs.getString(KEY_PASSWORD_HASH, null)
        val storedSaltString = prefs.getString(KEY_SALT, null)

        if (storedHash == null || storedSaltString == null) {
            return false
        }

        val storedSalt = Base64.decode(storedSaltString, Base64.NO_WRAP)
        val enteredPasswordHashed = hashPassword(enteredPassword, storedSalt)
        return storedHash == enteredPasswordHashed
    }

    // Yedekten geri yükleme için kullanılan harici kontrol metodu
    fun checkPassword(enteredPassword: String, saltBase64: String, hash: String): Boolean {
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val enteredPasswordHashed = hashPassword(enteredPassword, salt)
        return hash == enteredPasswordHashed
    }

    fun isPasswordSet(context: Context): Boolean {
        val prefs = getSharedPreferences()
        return prefs.getBoolean(KEY_IS_PASSWORD_ENABLED, false) &&
                prefs.getString(KEY_PASSWORD_HASH, null) != null
    }

    fun disablePassword(context: Context) {
        getSharedPreferences().edit(commit = true) {
            remove(KEY_PASSWORD_HASH)
            remove(KEY_SALT)
            putBoolean(KEY_IS_PASSWORD_ENABLED, false)
        }
    }

    fun getPasswordHash(context: Context): String? {
        return getSharedPreferences().getString(KEY_PASSWORD_HASH, null)
    }

    fun getSalt(context: Context): String? {
        return getSharedPreferences().getString(KEY_SALT, null)
    }

    fun restorePassword(context: Context, hash: String, salt: String) {
        getSharedPreferences().edit(commit = true) {
            putString(KEY_PASSWORD_HASH, hash)
            putString(KEY_SALT, salt)
            putBoolean(KEY_IS_PASSWORD_ENABLED, true)
        }
    }
}