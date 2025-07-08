package com.codenzi.snapnote

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.codenzi.snapnote.databinding.ActivityPasswordSettingsBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PasswordSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPasswordSettingsBinding
    private var currentThemeResId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        currentThemeResId = ThemeManager.getThemeResId(this)

        binding = ActivityPasswordSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarPasswordSettings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.password_settings_title)

        if (PasswordManager.isPasswordSet(this)) {
            binding.tilCurrentPassword.visibility = View.VISIBLE
            binding.btnDisablePassword.visibility = View.VISIBLE
        } else {
            binding.tilCurrentPassword.visibility = View.GONE
            binding.btnDisablePassword.visibility = View.GONE
        }

        binding.btnSavePassword.setOnClickListener {
            savePassword()
        }

        binding.btnDisablePassword.setOnClickListener {
            val currentPassword = binding.etCurrentPassword.text.toString()
            if (currentPassword.isBlank()) {
                Toast.makeText(this, getString(R.string.toast_enter_current_password_to_disable), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (PasswordManager.checkPassword(this, currentPassword)) {
                showDisablePasswordConfirmationDialog()
            } else {
                Toast.makeText(this, R.string.current_password_incorrect_error, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSecurityInfo.setOnClickListener {
            showSecurityInfoDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentThemeResId != ThemeManager.getThemeResId(this)) {
            recreate()
        }
    }

    private fun showSecurityInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.security_info_title))
            .setMessage(R.string.password_security_explanation)
            .setPositiveButton(getString(R.string.dialog_ok), null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
    // ... (PasswordSettingsActivity'deki diğer metodlar aynı kalacak)
    private fun savePassword() {
        val currentPassword = binding.etCurrentPassword.text.toString()
        val newPassword = binding.etNewPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()

        if (PasswordManager.isPasswordSet(this)) {
            if (!PasswordManager.checkPassword(this, currentPassword)) {
                Toast.makeText(this, R.string.current_password_incorrect_error, Toast.LENGTH_SHORT).show()
                return
            }
        }

        if (newPassword.isBlank() || confirmPassword.isBlank()) {
            Toast.makeText(this, getString(R.string.toast_password_fields_cannot_be_empty), Toast.LENGTH_SHORT).show()
            return
        }

        if (newPassword.length < 4) {
            Toast.makeText(this, R.string.password_too_short_error, Toast.LENGTH_SHORT).show()
            return
        }

        if (newPassword != confirmPassword) {
            Toast.makeText(this, R.string.password_mismatch_error, Toast.LENGTH_SHORT).show()
            return
        }

        PasswordManager.setPassword(this, newPassword)
        Toast.makeText(this, R.string.password_set_success, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun showDisablePasswordConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.password_disable_confirmation_title)
            .setMessage(R.string.password_disable_confirmation_message)
            .setPositiveButton(R.string.dialog_yes) { _, _ ->
                disablePassword()
            }
            .setNegativeButton(R.string.dialog_no, null)
            .show()
    }

    private fun disablePassword() {
        PasswordManager.disablePassword(this)
        Toast.makeText(this, R.string.password_disabled_success, Toast.LENGTH_SHORT).show()
        finish()
    }
}