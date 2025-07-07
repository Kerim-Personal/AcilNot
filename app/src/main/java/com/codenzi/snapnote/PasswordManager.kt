package com.codenzi.snapnote

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
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

    private const val PREFS_NAME = "AppSecurityPrefs" // Bu dosya adı artık şifreli olacak
    private const val KEY_PASSWORD_HASH = "password_hash"
    private const val KEY_SALT = "salt"
    private const val KEY_IS_PASSWORD_ENABLED = "is_password_enabled"

    /**
     * DÜZELTME: Standart SharedPreferences yerine EncryptedSharedPreferences kullanılıyor.
     * Bu fonksiyon, verileri disk üzerinde şifrelenmiş olarak saklayan güvenli bir SharedPreferences örneği oluşturur.
     */
    private fun getSharedPreferences(context: Context): SharedPreferences {
        // Ana şifreleme anahtarını oluşturur veya mevcut olanı alır.
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        // Şifreli SharedPreferences örneğini oluşturur ve döndürür.
        return EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
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