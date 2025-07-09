package com.codenzi.snapnote

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
        private val gson = Gson()

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

            findPreference<Preference>("google_drive_backup")?.setOnPreferenceClickListener {
                requestedAction = Action.BACKUP
                signInToGoogle()
                true
            }

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

                val credential = GoogleAccountCredential.usingOAuth2(
                    requireContext(),
                    listOf("https://www.googleapis.com/auth/drive.appdata")
                ).setSelectedAccount(account.account)

                val googleDriveManager = GoogleDriveManager(credential)

                when (requestedAction) {
                    Action.BACKUP -> backupNotes(googleDriveManager)
                    Action.RESTORE -> restoreNotes(googleDriveManager)
                    null -> {}
                }
            } catch (e: ApiException) {
                Log.w("SettingsFragment", "signInResult:failed code=" + e.statusCode)
                Toast.makeText(requireContext(), "Oturum açma hatası: Lütfen tekrar deneyin.", Toast.LENGTH_LONG).show()
            }
        }

        private fun backupNotes(googleDriveManager: GoogleDriveManager) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Yedekleme başlatılıyor...", Toast.LENGTH_SHORT).show()
                    }

                    val allNotes = noteDao.getAllNotes().first()
                    val notesWithData = allNotes.map { note ->
                        val content = gson.fromJson(note.content, NoteContent::class.java)

                        if (content.imagePath != null) {
                            try {
                                requireContext().contentResolver.openInputStream(content.imagePath!!.toUri())?.use { inputStream ->
                                    val bytes = inputStream.readBytes()
                                    content.imageDataBase64 = Base64.encodeToString(bytes, Base64.DEFAULT)
                                }
                            } catch (e: Exception) {
                                Log.e("Backup", "Resim dosyası okunamadı: ${content.imagePath}", e)
                            }
                        }

                        if (content.audioFilePath != null) {
                            try {
                                val file = File(content.audioFilePath!!)
                                if (file.exists()) {
                                    val bytes = file.readBytes()
                                    content.audioDataBase64 = Base64.encodeToString(bytes, Base64.DEFAULT)
                                }
                            } catch (e: Exception) {
                                Log.e("Backup", "Ses dosyası okunamadı: ${content.audioFilePath}", e)
                            }
                        }
                        note.copy(content = gson.toJson(content))
                    }

                    val notesJson = gson.toJson(notesWithData)
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

        private fun restoreNotes(googleDriveManager: GoogleDriveManager) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Yedekler aranıyor...", Toast.LENGTH_SHORT).show()
                    }

                    val backupFiles = googleDriveManager.getBackupFiles()
                    if (backupFiles.isNullOrEmpty()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), getString(R.string.backup_not_found), Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }

                    val file = backupFiles.first()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "'${file.name}' geri yükleniyor...", Toast.LENGTH_SHORT).show()
                    }

                    val jsonContent = googleDriveManager.downloadFile(file.id)
                    if (jsonContent.isNullOrBlank()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Yedek dosyası boş veya bozuk.", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }

                    val type = object : TypeToken<List<Note>>() {}.type
                    val notesFromBackup: List<Note> = gson.fromJson(jsonContent, type)

                    val restoredNotes = mutableListOf<Note>()
                    for (note in notesFromBackup) {
                        val content = gson.fromJson(note.content, NoteContent::class.java)

                        var finalImagePath = content.imagePath
                        if (content.imageDataBase64 != null) {
                            try {
                                val imageBytes = Base64.decode(content.imageDataBase64, Base64.DEFAULT)
                                val imageFile = createImageFile()
                                FileOutputStream(imageFile).use { it.write(imageBytes) }
                                finalImagePath = imageFile.toURI().toString()
                            } catch (e: Exception) {
                                Log.e("Restore", "Base64'ten resim oluşturulamadı", e)
                            }
                        }

                        var finalAudioPath = content.audioFilePath
                        if (content.audioDataBase64 != null) {
                            try {
                                val audioBytes = Base64.decode(content.audioDataBase64, Base64.DEFAULT)
                                val audioFile = createAudioFile()
                                FileOutputStream(audioFile).use { it.write(audioBytes) }
                                finalAudioPath = audioFile.absolutePath
                            } catch (e: Exception) {
                                Log.e("Restore", "Base64'ten ses oluşturulamadı", e)
                            }
                        }

                        val finalContent = content.copy(
                            imagePath = finalImagePath,
                            audioFilePath = finalAudioPath,
                            imageDataBase64 = null,
                            audioDataBase64 = null
                        )
                        restoredNotes.add(note.copy(content = gson.toJson(finalContent)))
                    }

                    noteDao.deleteAllNotes()
                    noteDao.insertAll(restoredNotes)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), getString(R.string.restore_success), Toast.LENGTH_LONG).show()
                    }

                } catch (e: Exception) {
                    showError(getString(R.string.restore_failed), e)
                }
            }
        }

        @Throws(IOException::class)
        private fun createImageFile(): File {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val storageDir: File = requireContext().getExternalFilesDir("RestoredImages")
                ?: requireContext().filesDir
            storageDir.mkdirs()
            return File.createTempFile("IMG_${timeStamp}_", ".jpg", storageDir)
        }

        @Throws(IOException::class)
        private fun createAudioFile(): File {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val storageDir: File = requireContext().getExternalFilesDir("RestoredAudio")
                ?: requireContext().filesDir
            storageDir.mkdirs()
            return File.createTempFile("AUD_${timeStamp}_", ".mp3", storageDir)
        }

        private suspend fun showError(message: String, e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "$message: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("SettingsFragment", message, e)
            }
        }
    }
}