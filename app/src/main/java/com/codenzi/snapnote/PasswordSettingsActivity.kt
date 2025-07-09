package com.codenzi.snapnote

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.codenzi.snapnote.databinding.ActivityPasswordSettingsBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Sadece parola bilgilerini tutacak data sınıfı
data class CredentialBackup(
    val passwordHash: String?,
    val salt: String?
)

// Hangi Drive işleminin istendiğini belirtmek için enum
private enum class DriveAction {
    BACKUP_PASSWORD,
    DELETE_PASSWORD
}

@AndroidEntryPoint
class PasswordSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPasswordSettingsBinding
    private var currentThemeResId: Int = 0
    private val gson = Gson()
    private var requestedDriveAction: DriveAction? = null

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            handleSignInResult(result.data)
        } else {
            val message = when(requestedDriveAction) {
                DriveAction.BACKUP_PASSWORD -> "Parola yerel olarak ayarlandı ancak Google Drive'a yedeklenemedi."
                DriveAction.DELETE_PASSWORD -> "Parola yerel olarak kaldırıldı ancak Google Drive yedeği kaldırılamadı."
                else -> "Google ile oturum açma iptal edildi."
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            finish()
        }
    }

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

        AlertDialog.Builder(this)
            .setTitle("Parola Yedeği")
            .setMessage("Yeni parolanız, olası bir unutma durumunda verilerinizi kurtarabilmeniz için Google Drive'a güvenli bir şekilde yedeklenecektir. Onaylıyor musunuz?")
            .setPositiveButton("Evet, Yedekle") { _, _ ->
                requestedDriveAction = DriveAction.BACKUP_PASSWORD
                signInToGoogle()
            }
            .setNegativeButton("Hayır") { _, _ ->
                Toast.makeText(this, R.string.password_set_success, Toast.LENGTH_SHORT).show()
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun showDisablePasswordConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.password_disable_confirmation_title)
            .setMessage("Parolayı devre dışı bırakmak istediğinizden emin misiniz? Bu işlem, Google Drive'daki parola yedeğinizi de silecektir.")
            .setPositiveButton(R.string.dialog_yes) { _, _ ->
                disablePassword()
            }
            .setNegativeButton(R.string.dialog_no, null)
            .show()
    }

    private fun disablePassword() {
        PasswordManager.disablePassword(this)
        requestedDriveAction = DriveAction.DELETE_PASSWORD
        signInToGoogle()
    }

    @Suppress("DEPRECATION")
    private fun signInToGoogle() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/drive.appdata"))
            .build()

        val googleSignInClient = GoogleSignIn.getClient(this, gso)

        googleSignInClient.signOut().addOnCompleteListener {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    @Suppress("DEPRECATION")
    private fun handleSignInResult(data: Intent?) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)!!

            val credential = GoogleAccountCredential.usingOAuth2(
                this,
                listOf("https://www.googleapis.com/auth/drive.appdata")
            ).setSelectedAccount(account.account)

            val googleDriveManager = GoogleDriveManager(credential)

            when(requestedDriveAction) {
                DriveAction.BACKUP_PASSWORD -> backupPasswordToDrive(googleDriveManager)
                DriveAction.DELETE_PASSWORD -> deletePasswordFromDrive(googleDriveManager)
                null -> finish() // Beklenmedik durum
            }

        } catch (e: ApiException) {
            Log.w("PasswordSettings", "signInResult:failed code=" + e.statusCode, e)
            Toast.makeText(this, "Google ile oturum açılamadı. Drive işlemi gerçekleştirilemedi.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun backupPasswordToDrive(googleDriveManager: GoogleDriveManager) {
        lifecycleScope.launch(Dispatchers.IO) {
            val passwordHash = PasswordManager.getPasswordHash(this@PasswordSettingsActivity)
            val salt = PasswordManager.getSalt(this@PasswordSettingsActivity)

            val credentialBackup = CredentialBackup(passwordHash, salt)
            val jsonContent = gson.toJson(credentialBackup)

            val success = googleDriveManager.uploadJsonBackup("snapnote_credentials.json", jsonContent)

            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(this@PasswordSettingsActivity, "Yeni parola başarıyla ayarlandı ve güvenle yedeklendi.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@PasswordSettingsActivity, "Parola ayarlandı ancak yedeklenemedi.", Toast.LENGTH_LONG).show()
                }
                finish()
            }
        }
    }

    private fun deletePasswordFromDrive(googleDriveManager: GoogleDriveManager) {
        lifecycleScope.launch(Dispatchers.IO) {
            val success = googleDriveManager.deleteFile("snapnote_credentials.json")
            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(this@PasswordSettingsActivity, "Parola kaldırıldı ve Drive yedeği silindi.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@PasswordSettingsActivity, "Parola kaldırıldı ancak Drive yedeği silinemedi.", Toast.LENGTH_LONG).show()
                }
                finish()
            }
        }
    }
}