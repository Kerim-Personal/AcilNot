// app/src/main/java/com/codenzi/acilnot/PasswordSettingsActivity.kt
package com.codenzi.acilnot

import android.os.Bundle
import android.view.View
import android.widget.Button
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

        // Parola zaten ayarlıysa mevcut parola alanını ve devre dışı bırakma butonunu göster
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
            showDisablePasswordConfirmationDialog()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun savePassword() {
        val currentPassword = etCurrentPassword.text.toString()
        val newPassword = etNewPassword.text.toString()
        val confirmPassword = etConfirmPassword.text.toString()

        // Eğer parola zaten ayarlıysa, mevcut parolayı doğrula
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
        finish() // Ayarlar ekranına geri dön
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
        // Parola devre dışı bırakılırken mevcut parolayı doğrulamak isteyebilirsiniz.
        // Basitlik adına bu örnekte doğrulamayı atlıyoruz, ancak gerçek bir uygulamada ekleyebilirsiniz.
        PasswordManager.disablePassword(this)
        Toast.makeText(this, R.string.password_disabled_success, Toast.LENGTH_SHORT).show()
        finish() // Ayarlar ekranına geri dön
    }
}
