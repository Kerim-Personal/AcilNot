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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
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

// Yedekleme verisini yapılandırmak için yeni data sınıfları
data class AppSettings(
    val themeSelection: String?,
    val colorSelection: String?,
    val widgetBackgroundSelection: String?
)

data class BackupData(
    val settings: AppSettings,
    val notes: List<Note>
)

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

            findPreference<ListPreference>("theme_selection")?.setOnPreferenceChangeListener { _, newValue ->
                val mode = when (newValue as String) {
                    "light" -> AppCompatDelegate.MODE_NIGHT_NO
                    "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                AppCompatDelegate.setDefaultNightMode(mode)
                true
            }

            findPreference<ListPreference>("color_selection")?.setOnPreferenceChangeListener { _, _ ->
                requireActivity().recreate()
                true
            }

            findPreference<ListPreference>("widget_background_selection")?.setOnPreferenceChangeListener { _, _ ->
                requireActivity().window.decorView.post {
                    updateAllWidgets()
                }
                true
            }

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

            findPreference<Preference>("password_settings")?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), PasswordSettingsActivity::class.java))
                true
            }

            findPreference<Preference>("trash_settings")?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), TrashActivity::class.java))
                true
            }

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
            val context = requireContext().applicationContext
            val appWidgetManager = AppWidgetManager.getInstance(context)

            val noteWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, NoteWidgetProvider::class.java))
            if (noteWidgetIds.isNotEmpty()) {
                val noteIntent = Intent(context, NoteWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, noteWidgetIds)
                }
                context.sendBroadcast(noteIntent)
            }

            val cameraWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, CameraWidgetProvider::class.java))
            if (cameraWidgetIds.isNotEmpty()) {
                val cameraIntent = Intent(context, CameraWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, cameraWidgetIds)
                }
                context.sendBroadcast(cameraIntent)
            }

            val voiceMemoWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, VoiceMemoWidgetProvider::class.java))
            if (voiceMemoWidgetIds.isNotEmpty()) {
                val voiceMemoIntent = Intent(context, VoiceMemoWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, voiceMemoWidgetIds)
                }
                context.sendBroadcast(voiceMemoIntent)
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

        // **DÜZENLENEN FONKSİYON**
        private fun backupNotes(googleDriveManager: GoogleDriveManager) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // Önce mevcut yedek ve lokal notları kontrol et
                    val existingBackupFiles = googleDriveManager.getBackupFiles()
                    val localNotes = noteDao.getAllNotes().first()

                    // Eğer Drive'da yedek varsa ve lokalde hiç not yoksa kullanıcıyı uyar
                    if (!existingBackupFiles.isNullOrEmpty() && localNotes.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            AlertDialog.Builder(requireContext())
                                .setTitle("Yedekleme Uyarısı")
                                .setMessage("Google Drive'da mevcut bir yedeğiniz bulundu. Mevcut boş not listenizle bu yedeğin üzerine yazmak, eski notlarınızı kalıcı olarak silecektir. Devam etmek istediğinizden emin misiniz?")
                                .setPositiveButton("Evet, Üzerine Yaz") { _, _ ->
                                    // Kullanıcı onaylarsa yedeklemeye devam et
                                    proceedWithBackup(googleDriveManager, localNotes)
                                }
                                .setNegativeButton("İptal", null)
                                .show()
                        }
                    } else {
                        // Eğer bir risk yoksa doğrudan yedeklemeye devam et
                        proceedWithBackup(googleDriveManager, localNotes)
                    }
                } catch (e: Exception) {
                    showError("Yedekleme kontrolü sırasında hata", e)
                }
            }
        }

        // **YENİ FONKSİYON:** Asıl yedekleme mantığını içerir.
        private fun proceedWithBackup(googleDriveManager: GoogleDriveManager, notesToBackup: List<Note>) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Yedekleme başlatılıyor...", Toast.LENGTH_SHORT).show()
                    }

                    val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
                    val appSettings = AppSettings(
                        themeSelection = sharedPrefs.getString("theme_selection", "system_default"),
                        colorSelection = sharedPrefs.getString("color_selection", "bordo"),
                        widgetBackgroundSelection = sharedPrefs.getString("widget_background_selection", "widget_background")
                    )

                    val notesForBackup = mutableListOf<Note>()
                    for (note in notesToBackup) {
                        val content = gson.fromJson(note.content, NoteContent::class.java)
                        var imageDriveId: String? = null
                        if (content.imagePath != null) {
                            val imageFile = try { content.imagePath?.toUri()?.path?.let { File(it) } } catch (e: Exception) { null }
                            if (imageFile?.exists() == true) {
                                imageDriveId = googleDriveManager.uploadMediaFile(imageFile, "image/jpeg")
                            }
                        }
                        var audioDriveId: String? = null
                        if (content.audioFilePath != null) {
                            val audioFile = File(content.audioFilePath)
                            if (audioFile.exists()) {
                                audioDriveId = googleDriveManager.uploadMediaFile(audioFile, "audio/mp4")
                            }
                        }
                        val newContent = content.copy(imagePath = imageDriveId, audioFilePath = audioDriveId)
                        notesForBackup.add(note.copy(content = gson.toJson(newContent)))
                    }

                    val backupData = BackupData(settings = appSettings, notes = notesForBackup)
                    val backupJson = gson.toJson(backupData)

                    val success = googleDriveManager.uploadJsonBackup("snapnote_backup.json", backupJson)

                    withContext(Dispatchers.Main) {
                        if (success) {
                            Toast.makeText(requireContext(), "Notlar ve ayarlar başarıyla yedeklendi!", Toast.LENGTH_SHORT).show()
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

                    val type = object : TypeToken<BackupData>() {}.type
                    val backupData: BackupData = gson.fromJson(jsonContent, type)

                    val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
                    with(sharedPrefs.edit()) {
                        putString("theme_selection", backupData.settings.themeSelection)
                        putString("color_selection", backupData.settings.colorSelection)
                        putString("widget_background_selection", backupData.settings.widgetBackgroundSelection)
                        apply()
                    }

                    val notesFromBackup = backupData.notes
                    val restoredNotes = mutableListOf<Note>()

                    for (note in notesFromBackup) {
                        val content = gson.fromJson(note.content, NoteContent::class.java)
                        var localImagePath: String? = null
                        if(content.imagePath != null) {
                            val imageDriveId = content.imagePath
                            val imageFile = createImageFile()
                            val success = googleDriveManager.downloadMediaFile(imageDriveId, imageFile)
                            if (success) {
                                localImagePath = imageFile.toURI().toString()
                            }
                        }
                        var localAudioPath: String? = null
                        if(content.audioFilePath != null) {
                            val audioDriveId = content.audioFilePath
                            val audioFile = createAudioFile()
                            val success = googleDriveManager.downloadMediaFile(audioDriveId, audioFile)
                            if (success) {
                                localAudioPath = audioFile.absolutePath
                            }
                        }
                        val finalContent = content.copy(imagePath = localImagePath, audioFilePath = localAudioPath)
                        restoredNotes.add(note.copy(content = gson.toJson(finalContent)))
                    }

                    noteDao.deleteAllNotes()
                    noteDao.insertAll(restoredNotes)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), getString(R.string.restore_success), Toast.LENGTH_LONG).show()
                        requireActivity().recreate()
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