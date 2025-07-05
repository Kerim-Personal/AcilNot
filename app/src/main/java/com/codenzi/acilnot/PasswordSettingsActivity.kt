package com.codenzi.acilnot

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class PasswordSettingsActivity : AppCompatActivity() {

    private lateinit var etCurrentPassword: TextInputEditText
    private lateinit var tilCurrentPassword: TextInputLayout
    private lateinit var etNewPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var saveButton: Button
    private lateinit var disableButton: Button
    private lateinit var infoButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_password_settings)

        val toolbar: Toolbar = findViewById(R.id.toolbar_password_settings)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.password_settings_title)

        etCurrentPassword = findViewById(R.id.et_current_password)
        tilCurrentPassword = findViewById(R.id.til_current_password)
        etNewPassword = findViewById(R.id.et_new_password)
        etConfirmPassword = findViewById(R.id.et_confirm_password)
        saveButton = findViewById(R.id.btn_save_password)
        disableButton = findViewById(R.id.btn_disable_password)
        infoButton = findViewById(R.id.btn_security_info)

        if (PasswordManager.isPasswordSet(this)) {
            tilCurrentPassword.visibility = View.VISIBLE
            disableButton.visibility = View.VISIBLE
        } else {
            tilCurrentPassword.visibility = View.GONE
            disableButton.visibility = View.GONE
        }

        saveButton.setOnClickListener {
            savePassword()
        }

        disableButton.setOnClickListener {
            val currentPassword = etCurrentPassword.text.toString()
            if (currentPassword.isBlank()) {
                Toast.makeText(this, "Lütfen parolayı devre dışı bırakmak için mevcut parolanızı girin.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (PasswordManager.checkPassword(this, currentPassword)) {
                showDisablePasswordConfirmationDialog()
            } else {
                Toast.makeText(this, R.string.current_password_incorrect_error, Toast.LENGTH_SHORT).show()
            }
        }

        infoButton.setOnClickListener {
            showSecurityInfoDialog()
        }
    }

    private fun showSecurityInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle("Güvenlik Bilgisi")
            .setMessage(R.string.password_security_explanation)
            .setPositiveButton("Tamam", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun savePassword() {
        val currentPassword = etCurrentPassword.text.toString()
        val newPassword = etNewPassword.text.toString()
        val confirmPassword = etConfirmPassword.text.toString()

        if (PasswordManager.isPasswordSet(this)) {
            if (!PasswordManager.checkPassword(this, currentPassword)) {
                Toast.makeText(this, R.string.current_password_incorrect_error, Toast.LENGTH_SHORT).show()
                return
            }
        }

        if (newPassword.isBlank() || confirmPassword.isBlank()) {
            Toast.makeText(this, "Parola alanları boş bırakılamaz.", Toast.LENGTH_SHORT).show()
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