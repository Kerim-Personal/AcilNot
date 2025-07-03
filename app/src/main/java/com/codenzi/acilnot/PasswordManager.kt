// app/src/main/java/com/codenzi/acilnot/PasswordManager.kt
package com.codenzi.acilnot

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Uygulama parolalarını güvenli bir şekilde yönetmek için yardımcı sınıf.
 * Parolalar doğrudan saklanmaz, bunun yerine SHA-256 ile hash'lenir ve tuzlanır.
 * Tuz ve hash, SharedPreferences'ta saklanır.
 */
object PasswordManager {

    private const val PREFS_NAME = "AppSecurityPrefs"
    private const val KEY_PASSWORD_HASH = "password_hash"
    private const val KEY_SALT = "salt"
    private const val KEY_IS_PASSWORD_ENABLED = "is_password_enabled"

    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Verilen parolayı SHA-256 kullanarak hash'ler ve rastgele bir tuz ekler.
     * @param password Hash'lenecek parola.
     * @param salt Kullanılacak tuz. Eğer null ise yeni bir tuz oluşturulur.
     * @return Parolanın hash'i (Base64 kodlu string).
     */
    private fun hashPassword(password: String, salt: ByteArray? = null): Pair<String, ByteArray> {
        val actualSalt = salt ?: generateSalt()
        val passwordBytes = password.toByteArray(Charsets.UTF_8)
        val combinedBytes = actualSalt + passwordBytes

        val digest = MessageDigest.getInstance("SHA-256")
        val hashedBytes = digest.digest(combinedBytes)

        return Base64.encodeToString(hashedBytes, Base64.NO_WRAP) to actualSalt
    }

    /**
     * Rastgele bir tuz (salt) oluşturur.
     * @return Rastgele oluşturulmuş tuz (byte dizisi).
     */
    private fun generateSalt(): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(16) // 16 byte = 128 bit tuz boyutu
        random.nextBytes(salt)
        return salt
    }

    /**
     * Uygulama parolasını ayarlar veya günceller.
     * @param context Uygulama bağlamı.
     * @param newPassword Yeni parola.
     */
    fun setPassword(context: Context, newPassword: String) {
        val (hashedPassword, salt) = hashPassword(newPassword)
        getSharedPreferences(context).edit().apply {
            putString(KEY_PASSWORD_HASH, hashedPassword)
            putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            putBoolean(KEY_IS_PASSWORD_ENABLED, true)
            apply()
        }
    }

    /**
     * Girilen parolanın kayıtlı parolayla eşleşip eşleşmediğini kontrol eder.
     * @param context Uygulama bağlamı.
     * @param enteredPassword Kullanıcının girdiği parola.
     * @return Parola doğruysa true, değilse false.
     */
    fun checkPassword(context: Context, enteredPassword: String): Boolean {
        val prefs = getSharedPreferences(context)
        val storedHash = prefs.getString(KEY_PASSWORD_HASH, null)
        val storedSaltString = prefs.getString(KEY_SALT, null)

        if (storedHash == null || storedSaltString == null) {
            return false // Parola ayarlanmamış
        }

        val storedSalt = Base64.decode(storedSaltString, Base64.NO_WRAP)
        val (enteredPasswordHashed, _) = hashPassword(enteredPassword, storedSalt)

        return storedHash == enteredPasswordHashed
    }

    /**
     * Uygulama parolasının ayarlanıp ayarlanmadığını kontrol eder.
     * @param context Uygulama bağlamı.
     * @return Parola ayarlanmışsa true, değilse false.
     */
    fun isPasswordSet(context: Context): Boolean {
        return getSharedPreferences(context).getBoolean(KEY_IS_PASSWORD_ENABLED, false) &&
                getSharedPreferences(context).getString(KEY_PASSWORD_HASH, null) != null
    }

    /**
     * Uygulama parolasını devre dışı bırakır (kaldırır).
     * @param context Uygulama bağlamı.
     */
    fun disablePassword(context: Context) {
        getSharedPreferences(context).edit().apply {
            remove(KEY_PASSWORD_HASH)
            remove(KEY_SALT)
            putBoolean(KEY_IS_PASSWORD_ENABLED, false)
            apply()
        }
    }
}
