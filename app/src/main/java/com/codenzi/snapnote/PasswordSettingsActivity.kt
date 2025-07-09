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
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class PasswordSettingsActivity : AppCompatActivity() {

    @Inject
    lateinit var noteDao: NoteDao // SettingsFragment'taki gibi, notları çekmek için DAO'yu enjekte ediyoruz.

    private lateinit var binding: ActivityPasswordSettingsBinding
    private val gson = Gson()
    private var requestedDriveAction: DriveAction? = null

    private enum class DriveAction {
        UPDATE_PASSWORD_IN_BACKUP,
        REMOVE_PASSWORD_FROM_BACKUP
    }

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            handleSignInResult(result.data)
        } else {
            Toast.makeText(this, "Google ile oturum açma iptal edildi. Parola değişikliği yedeklenemedi.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityPasswordSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarPasswordSettings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.password_settings_title)

        updateUI()

        binding.btnSavePassword.setOnClickListener { savePassword() }
        binding.btnDisablePassword.setOnClickListener { showDisablePasswordConfirmationDialog() }
        binding.btnSecurityInfo.setOnClickListener { showSecurityInfoDialog() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun updateUI() {
        if (PasswordManager.isPasswordSet(this)) {
            binding.tilCurrentPassword.visibility = View.VISIBLE
            binding.btnDisablePassword.visibility = View.VISIBLE
        } else {
            binding.tilCurrentPassword.visibility = View.GONE
            binding.btnDisablePassword.visibility = View.GONE
        }
    }

    private fun savePassword() {
        val currentPassword = binding.etCurrentPassword.text.toString()
        val newPassword = binding.etNewPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()

        if (PasswordManager.isPasswordSet(this) && !PasswordManager.checkPassword(this, currentPassword)) {
            Toast.makeText(this, R.string.current_password_incorrect_error, Toast.LENGTH_SHORT).show()
            return
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
        Toast.makeText(this, "Parola yerel olarak ayarlandı. Google Drive yedeği güncelleniyor...", Toast.LENGTH_SHORT).show()

        requestedDriveAction = DriveAction.UPDATE_PASSWORD_IN_BACKUP
        triggerDriveUpdate()
    }

    private fun showDisablePasswordConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.password_disable_confirmation_title)
            .setMessage("Şifreyi devre dışı bırakmak istediğinizden emin misiniz? Bu işlem, Google Drive'daki yedeğinizi de güncelleyecektir.")
            .setPositiveButton(R.string.dialog_yes) { _, _ -> disablePassword() }
            .setNegativeButton(R.string.dialog_no, null)
            .show()
    }

    private fun disablePassword() {
        val currentPassword = binding.etCurrentPassword.text.toString()
        if (!PasswordManager.checkPassword(this, currentPassword)) {
            Toast.makeText(this, R.string.current_password_incorrect_error, Toast.LENGTH_SHORT).show()
            return
        }

        PasswordManager.disablePassword(this)
        Toast.makeText(this, "Parola yerel olarak kaldırıldı. Google Drive yedeği güncelleniyor...", Toast.LENGTH_SHORT).show()

        requestedDriveAction = DriveAction.REMOVE_PASSWORD_FROM_BACKUP
        triggerDriveUpdate()
    }

    private fun triggerDriveUpdate() {
        val lastSignedInAccount = GoogleSignIn.getLastSignedInAccount(this)
        val driveScope = Scope("https://www.googleapis.com/auth/drive.appdata")

        if (lastSignedInAccount != null && lastSignedInAccount.grantedScopes.contains(driveScope)) {
            proceedWithDriveAction(lastSignedInAccount)
        } else {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(driveScope)
                .build()
            val googleSignInClient = GoogleSignIn.getClient(this, gso)
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    @Suppress("DEPRECATION")
    private fun handleSignInResult(data: Intent?) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            proceedWithDriveAction(task.getResult(ApiException::class.java)!!)
        } catch (e: ApiException) {
            Log.w("PasswordSettings", "signInResult:failed code=" + e.statusCode, e)
            Toast.makeText(this, "Google ile oturum açılamadı. Parola değişikliği yedeklenemedi.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun proceedWithDriveAction(account: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(this, setOf("https://www.googleapis.com/auth/drive.appdata"))
            .setSelectedAccount(account.account)
        val googleDriveManager = GoogleDriveManager(credential)

        lifecycleScope.launch(Dispatchers.IO) {
            val backupFile = googleDriveManager.getBackupFiles()?.firstOrNull()
            if (backupFile == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PasswordSettingsActivity, "Önce not yedeği oluşturmalısınız. Parola değişikliği yedeklenemedi.", Toast.LENGTH_LONG).show()
                    finish()
                }
                return@launch
            }

            val jsonContent = googleDriveManager.downloadJsonBackup(backupFile.id)
            if (jsonContent.isNullOrBlank()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PasswordSettingsActivity, "Yedek dosyası bozuk. Yeni bir yedek oluşturun.", Toast.LENGTH_LONG).show()
                    finish()
                }
                return@launch
            }

            val type = object : TypeToken<BackupData>() {}.type
            val backupData: BackupData = gson.fromJson(jsonContent, type)

            val updatedBackupData = when (requestedDriveAction) {
                DriveAction.UPDATE_PASSWORD_IN_BACKUP -> backupData.copy(
                    passwordHash = PasswordManager.getPasswordHash(this@PasswordSettingsActivity),
                    salt = PasswordManager.getSalt(this@PasswordSettingsActivity)
                )
                DriveAction.REMOVE_PASSWORD_FROM_BACKUP -> backupData.copy(
                    passwordHash = null,
                    salt = null
                )
                else -> backupData
            }

            val updatedJson = gson.toJson(updatedBackupData)
            val success = googleDriveManager.uploadJsonBackup(backupFile.name, updatedJson)

            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(this@PasswordSettingsActivity, "Parola değişikliği Google Drive yedeğine başarıyla yansıtıldı.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@PasswordSettingsActivity, "Parola değiştirildi ancak Drive yedeği güncellenemedi.", Toast.LENGTH_LONG).show()
                }
                finish()
            }
        }
    }

    private fun showSecurityInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.security_info_title))
            .setMessage(R.string.password_security_explanation)
            .setPositiveButton(getString(R.string.dialog_ok), null)
            .show()
    }
}