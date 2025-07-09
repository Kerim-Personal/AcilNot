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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class PasswordSettingsActivity : AppCompatActivity() {

    @Inject
    lateinit var noteDao: NoteDao
    private lateinit var binding: ActivityPasswordSettingsBinding
    private val gson = Gson()

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
        Toast.makeText(this, "Parola ayarlandı. Otomatik yedekleme başlatılıyor...", Toast.LENGTH_SHORT).show()
        triggerAutomaticBackup()
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
        Toast.makeText(this, "Parola kaldırıldı. Otomatik yedekleme başlatılıyor...", Toast.LENGTH_SHORT).show()
        triggerAutomaticBackup()
    }

    private fun triggerAutomaticBackup() {
        val lastSignedInAccount = GoogleSignIn.getLastSignedInAccount(this)
        val driveScope = Scope("https://www.googleapis.com/auth/drive.appdata")

        if (lastSignedInAccount != null && lastSignedInAccount.grantedScopes.contains(driveScope)) {
            performAutomaticBackup(lastSignedInAccount)
        } else {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(driveScope)
                .build()
            val googleSignInClient = GoogleSignIn.getClient(this, gso)
            googleSignInClient.signOut().addOnCompleteListener {
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun handleSignInResult(data: Intent?) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            performAutomaticBackup(task.getResult(ApiException::class.java)!!)
        } catch (e: ApiException) {
            Log.w("PasswordSettings", "signInResult:failed code=" + e.statusCode, e)
            Toast.makeText(this, "Google ile oturum açılamadı. Parola değişikliği yedeklenemedi.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun performAutomaticBackup(account: GoogleSignInAccount) {
        lifecycleScope.launch(Dispatchers.IO) {
            val credential = GoogleAccountCredential.usingOAuth2(
                this@PasswordSettingsActivity,
                listOf("https://www.googleapis.com/auth/drive.appdata")
            ).setSelectedAccount(account.account)
            val googleDriveManager = GoogleDriveManager(credential)

            try {
                val notesToBackup = noteDao.getAllNotes().first()
                proceedWithFullBackup(googleDriveManager, notesToBackup)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PasswordSettingsActivity, "Yedekleme sırasında hata: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private suspend fun proceedWithFullBackup(googleDriveManager: GoogleDriveManager, notesToBackup: List<Note>) {
        withContext(Dispatchers.Main) {
            Toast.makeText(this@PasswordSettingsActivity, "Google Drive yedeği güncelleniyor...", Toast.LENGTH_SHORT).show()
        }

        try {
            val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
            val appSettings = AppSettings(
                themeSelection = sharedPrefs.getString("theme_selection", "system_default"),
                colorSelection = sharedPrefs.getString("color_selection", "bordo"),
                widgetBackgroundSelection = sharedPrefs.getString("widget_background_selection", "widget_background")
            )

            val passwordHash = PasswordManager.getPasswordHash(this)
            val salt = PasswordManager.getSalt(this)

            val notesForBackup = mutableListOf<Note>()
            for (note in notesToBackup) {
                val content = gson.fromJson(note.content, NoteContent::class.java)
                var imageDriveId: String? = null
                content.imagePath?.let { path ->
                    val imageFile = try { File(path.toUri().path!!) } catch (e: Exception) { null }
                    if (imageFile?.exists() == true) {
                        imageDriveId = googleDriveManager.uploadMediaFile(imageFile, "image/jpeg")
                    }
                }
                var audioDriveId: String? = null
                content.audioFilePath?.let { path ->
                    val audioFile = File(path)
                    if (audioFile.exists()) {
                        audioDriveId = googleDriveManager.uploadMediaFile(audioFile, "audio/mp4")
                    }
                }
                val newContent = content.copy(imagePath = imageDriveId, audioFilePath = audioDriveId)
                notesForBackup.add(note.copy(content = gson.toJson(newContent)))
            }

            val backupData = BackupData(
                settings = appSettings,
                notes = notesForBackup,
                passwordHash = passwordHash,
                salt = salt
            )
            val backupJson = gson.toJson(backupData)

            val success = googleDriveManager.uploadJsonBackup("snapnote_backup.json", backupJson)

            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(this@PasswordSettingsActivity, "Parola değişikliği Google Drive yedeğine başarıyla yansıtıldı.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@PasswordSettingsActivity, "Parola değiştirildi ancak Drive yedeği güncellenemedi.", Toast.LENGTH_LONG).show()
                }
                finish()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@PasswordSettingsActivity, "Yedekleme başarısız: ${e.message}", Toast.LENGTH_LONG).show()
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