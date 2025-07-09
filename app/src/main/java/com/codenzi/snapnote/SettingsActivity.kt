package com.codenzi.snapnote

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
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

            // Tema Değişikliği
            findPreference<ListPreference>("theme_selection")?.setOnPreferenceChangeListener { _, newValue ->
                val mode = when (newValue as String) {
                    "light" -> AppCompatDelegate.MODE_NIGHT_NO
                    "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                AppCompatDelegate.setDefaultNightMode(mode)
                true
            }

            // Renk Değişikliği
            findPreference<ListPreference>("color_selection")?.setOnPreferenceChangeListener { _, _ ->
                requireActivity().recreate()
                true
            }

            // Widget Arka Planı Değişikliği
            findPreference<ListPreference>("widget_background_selection")?.setOnPreferenceChangeListener { _, _ ->
                // Değişikliğin SharedPreferences'a yansıması için küçük bir gecikme ekliyoruz.
                activity?.window?.decorView?.postDelayed({
                    updateAllWidgets()
                }, 100)
                true
            }


            // Google Drive Yedekleme
            findPreference<Preference>("google_drive_backup")?.setOnPreferenceClickListener {
                requestedAction = Action.BACKUP
                signInToGoogle()
                true
            }

            // Google Drive Geri Yükleme
            findPreference<Preference>("google_drive_restore")?.setOnPreferenceClickListener {
                requestedAction = Action.RESTORE
                signInToGoogle()
                true
            }

            // Şifre Ayarları
            findPreference<Preference>("password_settings")?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), PasswordSettingsActivity::class.java))
                true
            }

            // Çöp Kutusu
            findPreference<Preference>("trash_settings")?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), TrashActivity::class.java))
                true
            }

            // Gizlilik Politikası
            findPreference<Preference>("privacy_policy")?.setOnPreferenceClickListener {
                val url = "https://codenzi.com/snapnote"
                val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), getString(R.string.toast_no_browser_found), Toast.LENGTH_SHORT).show()
                }
                true
            }

            // Bize Ulaşın
            findPreference<Preference>("contact_us")?.setOnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = "mailto:".toUri()
                    putExtra(Intent.EXTRA_EMAIL, arrayOf("info@codenzi.com"))
                    putExtra(Intent.EXTRA_SUBJECT, getString(R.string.contact_us_email_subject))
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), getString(R.string.toast_no_email_app_found), Toast.LENGTH_SHORT).show()
                }
                true
            }
        }

        private fun updateAllWidgets() {
            val context = requireActivity().applicationContext
            val appWidgetManager = AppWidgetManager.getInstance(context)

            // NoteWidgetProvider'ı güncelle
            val noteWidgetComponentName = ComponentName(context, NoteWidgetProvider::class.java)
            val noteWidgetIds = appWidgetManager.getAppWidgetIds(noteWidgetComponentName)
            for (appWidgetId in noteWidgetIds) {
                NoteWidgetProvider.updateAppWidget(context, appWidgetManager, appWidgetId)
            }

            // VoiceMemoWidgetProvider'ı güncelle
            val voiceMemoWidgetComponentName = ComponentName(context, VoiceMemoWidgetProvider::class.java)
            val voiceMemoWidgetIds = appWidgetManager.getAppWidgetIds(voiceMemoWidgetComponentName)
            for (appWidgetId in voiceMemoWidgetIds) {
                VoiceMemoWidgetProvider.updateAppWidget(context, appWidgetManager, appWidgetId)
            }

            // CameraWidgetProvider'ı güncelle
            val cameraWidgetComponentName = ComponentName(context, CameraWidgetProvider::class.java)
            val cameraWidgetIds = appWidgetManager.getAppWidgetIds(cameraWidgetComponentName)
            for (appWidgetId in cameraWidgetIds) {
                CameraWidgetProvider.updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }


        @Suppress("DEPRECATION")
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

        @Suppress("DEPRECATION")
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
                    val notesForBackup = mutableListOf<Note>()

                    for (note in allNotes) {
                        val content = gson.fromJson(note.content, NoteContent::class.java)

                        var imageDriveId: String? = null
                        if (content.imagePath != null) {
                            // URI'dan geçerli bir dosya yolu elde etmeye çalışın
                            val imageFile = try {
                                content.imagePath?.toUri()?.path?.let { File(it) }
                            } catch (e: Exception) { null }

                            if(imageFile?.exists() == true) {
                                imageDriveId = googleDriveManager.uploadMediaFile(imageFile, "image/jpeg")
                            }
                        }

                        var audioDriveId: String? = null
                        if (content.audioFilePath != null) {
                            val audioFile = File(content.audioFilePath)
                            if(audioFile.exists()) {
                                audioDriveId = googleDriveManager.uploadMediaFile(audioFile, "audio/mp4")
                            }
                        }

                        // Not içeriğini Drive ID'leri ile güncelle
                        val newContent = content.copy(
                            imagePath = imageDriveId, // imagePath artık Drive ID'sini tutuyor
                            audioFilePath = audioDriveId // audioFilePath artık Drive ID'sini tutuyor
                        )
                        notesForBackup.add(note.copy(content = gson.toJson(newContent)))
                    }

                    val notesJson = gson.toJson(notesForBackup)
                    val success = googleDriveManager.uploadJsonBackup("snapnote_backup.json", notesJson)

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
                        withContext(Dispatchers.Main) { Toast.makeText(requireContext(), getString(R.string.backup_not_found), Toast.LENGTH_LONG).show() }
                        return@launch
                    }

                    val file = backupFiles.first()
                    withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "'${file.name}' geri yükleniyor...", Toast.LENGTH_SHORT).show() }

                    val jsonContent = googleDriveManager.downloadJsonBackup(file.id)
                    if (jsonContent.isNullOrBlank()) {
                        withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "Yedek dosyası boş veya bozuk.", Toast.LENGTH_LONG).show() }
                        return@launch
                    }

                    val type = object : TypeToken<List<Note>>() {}.type
                    val notesFromBackup: List<Note> = gson.fromJson(jsonContent, type)
                    val restoredNotes = mutableListOf<Note>()

                    for (note in notesFromBackup) {
                        val content = gson.fromJson(note.content, NoteContent::class.java)

                        var localImagePath: String? = null
                        if(content.imagePath != null) { // imagePath artık Drive ID'si
                            val imageDriveId = content.imagePath
                            val imageFile = createImageFile()
                            val success = googleDriveManager.downloadMediaFile(imageDriveId, imageFile)
                            if (success) {
                                localImagePath = imageFile.toURI().toString()
                            }
                        }

                        var localAudioPath: String? = null
                        if(content.audioFilePath != null) { // audioFilePath artık Drive ID'si
                            val audioDriveId = content.audioFilePath
                            val audioFile = createAudioFile()
                            val success = googleDriveManager.downloadMediaFile(audioDriveId, audioFile)
                            if (success) {
                                localAudioPath = audioFile.absolutePath
                            }
                        }

                        val finalContent = content.copy(
                            imagePath = localImagePath,
                            audioFilePath = localAudioPath
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