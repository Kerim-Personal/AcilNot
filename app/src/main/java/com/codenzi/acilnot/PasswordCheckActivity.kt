// kerim-personal/acilnot/AcilNot-44c6524bf431f2f8005232b49dde9c80bbce21fe/app/src/main/java/com/codenzi/acilnot/PasswordCheckActivity.kt

package com.codenzi.acilnot

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.OnBackPressedCallback // 1. ADIM: Gerekli sınıfı import edin
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.google.android.material.textfield.TextInputEditText

class PasswordCheckActivity : AppCompatActivity() {

    private lateinit var etUnlockPassword: TextInputEditText
    private lateinit var btnUnlock: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        super.onCreate(savedInstanceState)

        if (!PasswordManager.isPasswordSet(this)) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_password_check)

        etUnlockPassword = findViewById(R.id.et_unlock_password)
        btnUnlock = findViewById(R.id.btn_unlock)

        btnUnlock.setOnClickListener {
            checkPasswordAndUnlock()
        }

        // 2. ADIM: OnBackPressedDispatcher'ı burada yapılandırın
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Geri tuşuna basıldığında uygulamanın tamamen kapanması için
                finishAffinity()
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }

    private fun applySavedTheme() {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        val themeModeString = sharedPrefs.getString("theme_selection", "system_default")
        val mode = when (themeModeString) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun checkPasswordAndUnlock() {
        val enteredPassword = etUnlockPassword.text.toString()

        if (PasswordManager.checkPassword(this, enteredPassword)) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        } else {
            Toast.makeText(this, R.string.incorrect_password_error, Toast.LENGTH_SHORT).show()
        }
    }

    // 3. ADIM: Eski onBackPressed metodunu tamamen silin.
    // override fun onBackPressed() { ... }
}