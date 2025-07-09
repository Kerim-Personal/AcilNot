package com.codenzi.snapnote

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar: Toolbar = findViewById(R.id.settings_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    @AndroidEntryPoint
    class SettingsFragment : PreferenceFragmentCompat() {

        @Inject
        lateinit var noteDao: NoteDao

        // Hangi işlem için oturum açıldığını tutacak bir değişken
        private var requestedAction: Action? = null
        enum class Action { BACKUP, RESTORE }

        private val googleSignInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                handleSignInResult(result.data)
            } else {
                Toast.makeText(requireContext(), "Google ile oturum açma iptal edildi.", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            // Yedekle butonu için listener
            findPreference<Preference>("google_drive_backup")?.setOnPreferenceClickListener {
                requestedAction = Action.BACKUP
                signInToGoogle()
                true
            }

            // Geri Yükle butonu için listener
            findPreference<Preference>("google_drive_restore")?.setOnPreferenceClickListener {
                requestedAction = Action.RESTORE
                signInToGoogle()
                true
            }
        }

        private fun signInToGoogle() {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope("https://www.googleapis.com/auth/drive.appdata"))
                .build()

            val googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)

            googleSignInClient.signOut().addOnCompleteListener {
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            }
        }

        private fun handleSignInResult(data: Intent?) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.getResult(ApiException::class.java)

                // Hangi butona basıldıysa o işlemi yap
                when (requestedAction) {
                    Action.BACKUP -> backupNotes(account)
                    Action.RESTORE -> restoreNotes(account)
                    null -> {} // Bir hata durumu, normalde olmamalı
                }
            } catch (e: ApiException) {
                Log.w("SettingsFragment", "signInResult:failed code=" + e.statusCode)
                Toast.makeText(requireContext(), "Oturum açma hatası: Lütfen tekrar deneyin.", Toast.LENGTH_LONG).show()
            }
        }

        private fun backupNotes(account: GoogleSignInAccount) {
            lifecycleScope.launch {
                try {
                    val accessToken = getAccessToken(account) ?: return@launch
                    Toast.makeText(requireContext(), "Yedekleme başlatılıyor...", Toast.LENGTH_SHORT).show()

                    val allNotes = noteDao.getAllNotes().first()
                    val notesJson = Gson().toJson(allNotes)

                    val googleDriveManager = GoogleDriveManager(accessToken)
                    val success = googleDriveManager.uploadBackup("snapnote_backup.json", notesJson)

                    withContext(Dispatchers.Main) {
                        if (success) {
                            Toast.makeText(requireContext(), "Notlar başarıyla yedeklendi!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), "Yedekleme sırasında bir hata oluştu.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    showError("Yedekleme başarısız", e)
                }
            }
        }

        private fun restoreNotes(account: GoogleSignInAccount) {
            lifecycleScope.launch {
                try {
                    val accessToken = getAccessToken(account) ?: return@launch
                    Toast.makeText(requireContext(), "Yedekler aranıyor...", Toast.LENGTH_SHORT).show()

                    val googleDriveManager = GoogleDriveManager(accessToken)
                    val backupFiles = googleDriveManager.getBackupFiles()

                    if (backupFiles.isNullOrEmpty()) {
                        Toast.makeText(requireContext(), "Geri yüklenecek yedek bulunamadı.", Toast.LENGTH_LONG).show()
                        return@launch
                    }

                    val file = backupFiles.first()

                    Toast.makeText(requireContext(), "'${file.name}' geri yükleniyor...", Toast.LENGTH_SHORT).show()
                    val jsonContent = googleDriveManager.downloadFile(file.id)

                    if (jsonContent.isNullOrBlank()) {
                        Toast.makeText(requireContext(), "Yedek dosyası boş veya bozuk.", Toast.LENGTH_LONG).show()
                        return@launch
                    }

                    val type = object : TypeToken<List<Note>>() {}.type
                    val notesToRestore: List<Note> = Gson().fromJson(jsonContent, type)

                    withContext(Dispatchers.IO) {
                        noteDao.hardDeleteByIds(noteDao.getAllNotes().first().map { it.id })
                        notesToRestore.forEach { noteDao.insert(it) }
                    }

                    Toast.makeText(requireContext(), "Notlar başarıyla geri yüklendi!", Toast.LENGTH_LONG).show()

                } catch (e: Exception) {
                    showError("Geri yükleme başarısız", e)
                }
            }
        }

        private suspend fun getAccessToken(account: GoogleSignInAccount): String? {
            return withContext(Dispatchers.IO) {
                try {
                    val scope = "oauth2:https://www.googleapis.com/auth/drive.appdata"
                    GoogleAuthUtil.getToken(requireContext(), account.account!!, scope)
                } catch (e: Exception) {
                    showError("Erişim anahtarı alınamadı", e)
                    null
                }
            }
        }

        private suspend fun showError(message: String, e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "$message: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("SettingsFragment", message, e)
            }
        }
    }
}