package com.codenzi.snapnote

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.codenzi.snapnote.databinding.ActivityPasswordCheckBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PasswordCheckActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPasswordCheckBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // Dinamik renk temasını uygula
        ThemeManager.applyTheme(this)

        // Mevcut Açık/Koyu tema ayarını uygula
        applySavedTheme()
        super.onCreate(savedInstanceState)

        // Eğer şifre belirlenmemişse, doğrudan ana aktiviteye git
        if (!PasswordManager.isPasswordSet(this)) {
            navigateToMain()
            return
        }

        binding = ActivityPasswordCheckBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnUnlock.setOnClickListener {
            checkPasswordAndUnlock()
        }

        binding.etUnlockPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                checkPasswordAndUnlock()
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }

        // Geri tuşuna basıldığında uygulamayı tamamen kapatmak için
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
        hideKeyboard()
        val enteredPassword = binding.etUnlockPassword.text.toString()

        if (PasswordManager.checkPassword(this, enteredPassword)) {
            navigateToMain()
        } else {
            Toast.makeText(this, R.string.incorrect_password_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            // Bu bayraklar, MainActivity'yi yeni bir görevde başlatır ve
            // mevcut görevi (şifre ekranını içeren) temizler.
            // Bu, kullanıcı geri tuşuna bastığında şifre ekranına dönmesini engeller
            // ve aktivite geçişi sırasında uygulamanın kapanması sorununu çözer.
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        finish()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        var view = currentFocus
        if (view == null) {
            view = View(this)
        }
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}