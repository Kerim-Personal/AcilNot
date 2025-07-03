// app/src/main/java/com/codenzi/acilnot/PasswordCheckActivity.kt
package com.codenzi.acilnot

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

class PasswordCheckActivity : AppCompatActivity() {

    private lateinit var etUnlockPassword: TextInputEditText
    private lateinit var btnUnlock: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Uygulama ilk açıldığında veya parola ayarlı değilse doğrudan MainActivity'ye git
        if (!PasswordManager.isPasswordSet(this)) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish() // Bu aktiviteyi kapat
            return // Kalan onCreate kodunu çalıştırma
        }

        // Parola ayarlıysa, parola kontrol ekranını göster
        setContentView(R.layout.activity_password_check)

        etUnlockPassword = findViewById(R.id.et_unlock_password)
        btnUnlock = findViewById(R.id.btn_unlock)

        btnUnlock.setOnClickListener {
            checkPasswordAndUnlock()
        }
    }

    private fun checkPasswordAndUnlock() {
        val enteredPassword = etUnlockPassword.text.toString()

        if (PasswordManager.checkPassword(this, enteredPassword)) {
            // Parola doğru, ana aktiviteye git
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish() // Bu aktiviteyi kapat
        } else {
            Toast.makeText(this, R.string.incorrect_password_error, Toast.LENGTH_SHORT).show()
        }
    }

    // Kullanıcı geri tuşuna bastığında uygulamadan çıkmasını sağla
    override fun onBackPressed() {
        finishAffinity() // Tüm aktiviteleri kapat ve uygulamadan çık
    }
}
