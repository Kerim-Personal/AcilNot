package com.codenzi.acilnot

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
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

        // DÜZELTME: Klavyedeki "Bitti" tuşuna basıldığında kilit açma işlemini tetikle
        etUnlockPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                checkPasswordAndUnlock()
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }


        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
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
        hideKeyboard() // DÜZELTME: İşlem öncesi klavyeyi gizle
        val enteredPassword = etUnlockPassword.text.toString()

        if (PasswordManager.checkPassword(this, enteredPassword)) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        } else {
            Toast.makeText(this, R.string.incorrect_password_error, Toast.LENGTH_SHORT).show()
        }
    }

    // DÜZELTME: Klavyeyi gizlemek için yardımcı fonksiyon
    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        var view = currentFocus
        if (view == null) {
            view = View(this)
        }
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}