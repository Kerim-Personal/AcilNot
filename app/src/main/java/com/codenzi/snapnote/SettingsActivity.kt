package com.codenzi.snapnote

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.content.edit
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
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// Data sınıfları aynı kalıyor
data class AppSettings(
    val themeSelection: String?,
    val colorSelection: String?,
    val widgetBackgroundSelection: String?
)

data class BackupData(
    val settings: AppSettings,
    val notes: List<Note>,
    val passwordHash: String?,
    val salt: String?
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

        // İlerleme diyaloğu için değişkenler
        private var progressDialog: AlertDialog? = null
        private var progressBar: ProgressBar? = null
        private var progressTitle: TextView? = null
        private var progressPercentage: TextView? = null

        private val googleSignInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                handleSignInResult(result.data)
            } else {
                Toast.makeText(requireContext(), getString(R.string.google_sign_in_cancelled), Toast.LENGTH_SHORT).show()            }
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
                activity?.recreate()
                true
            }

            findPreference<ListPreference>("widget_background_selection")?.setOnPreferenceChangeListener { _, _ ->
                activity?.window?.decorView?.post {
                    updateAllWidgets()
                }
                true
            }

            findPreference<Preference>("add_widget_shortcut")?.setOnPreferenceClickListener {
                showAddWidgetDialog()
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

        private fun showAddWidgetDialog() {
            val widgetOptions = arrayOf(
                getString(R.string.widget_option_note_list),
                getString(R.string.widget_option_camera_note),
                getString(R.string.widget_option_voice_note)
            )
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.add_widget_dialog_title))
                .setItems(widgetOptions) { _, which ->
                    val componentName = when (which) {
                        0 -> ComponentName(requireActivity(), NoteWidgetProvider::class.java)
                        1 -> ComponentName(requireActivity(), CameraWidgetProvider::class.java)
                        2 -> ComponentName(requireActivity(), VoiceMemoWidgetProvider::class.java)
                        else -> null
                    }
                    componentName?.let { requestPinWidget(it) }
                }
                .show()
        }

        private fun requestPinWidget(componentName: ComponentName) {
            val appWidgetManager = AppWidgetManager.getInstance(requireContext())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (appWidgetManager.isRequestPinAppWidgetSupported) {
                    appWidgetManager.requestPinAppWidget(componentName, null, null)
                } else {
                    Toast.makeText(requireContext(), "Your launcher (home screen app) does not support this feature.", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(requireContext(), "This feature is available on Android 8.0 (Oreo) and above.", Toast.LENGTH_LONG).show()
            }
        }

        private fun updateAllWidgets() {
            val context = context?.applicationContext ?: return
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
                val account = task.getResult(ApiException::class.java)!!

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
                Log.w("SettingsFragment", "signInResult:failed code=" + e.statusCode, e)
                Toast.makeText(requireContext(), "Sign-in error: Please try again.", Toast.LENGTH_LONG).show()
            }
        }

        private fun showProgressDialog(titleResId: Int) {
            val builder = AlertDialog.Builder(requireContext())
            val inflater = requireActivity().layoutInflater
            val dialogView = inflater.inflate(R.layout.dialog_progress, null)

            progressBar = dialogView.findViewById(R.id.progress_bar)
            progressTitle = dialogView.findViewById(R.id.tv_progress_title)
            progressPercentage = dialogView.findViewById(R.id.tv_progress_percentage)

            progressTitle?.text = getString(titleResId)

            builder.setView(dialogView)
            builder.setCancelable(false)
            progressDialog = builder.create()
            progressDialog?.show()
        }

        private fun updateProgress(progress: Int) {
            progressBar?.progress = progress
            progressPercentage?.text = "$progress%"
        }

        private fun dismissProgressDialog() {
            progressDialog?.dismiss()
            progressDialog = null
        }

        private fun backupNotes(googleDriveManager: GoogleDriveManager) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val existingBackup = googleDriveManager.getBackupFiles()?.firstOrNull()
                    val localNotes = noteDao.getAllNotes().first()

                    withContext(Dispatchers.Main) {
                        if (existingBackup != null) {
                            AlertDialog.Builder(requireContext())
                                .setTitle("Existing Backup Found")
                                .setMessage("You already have a backup on Google Drive. Are you sure you want to overwrite it? This action cannot be undone.")
                                .setPositiveButton("Yes, Overwrite") { _, _ ->
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        proceedWithBackup(googleDriveManager, localNotes)
                                    }
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        } else {
                            proceedWithBackup(googleDriveManager, localNotes)
                        }
                    }
                } catch (e: Exception) {
                    showError("Error during backup", e)
                }
            }
        }

        private suspend fun proceedWithBackup(googleDriveManager: GoogleDriveManager, notesToBackup: List<Note>) {
            withContext(Dispatchers.Main) {
                showProgressDialog(R.string.backup_in_progress)
            }

            try {
                val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
                val appSettings = AppSettings(
                    themeSelection = sharedPrefs.getString("theme_selection", "system_default"),
                    colorSelection = sharedPrefs.getString("color_selection", "bordo"),
                    widgetBackgroundSelection = sharedPrefs.getString("widget_background_selection", "widget_background")
                )

                val passwordHash = if (PasswordManager.isPasswordSet(requireContext())) PasswordManager.getPasswordHash(requireContext()) else null
                val salt = if (PasswordManager.isPasswordSet(requireContext())) PasswordManager.getSalt(requireContext()) else null

                val notesForBackup = mutableListOf<Note>()
                val totalSteps = notesToBackup.size + 1

                notesToBackup.forEachIndexed { index, note ->
                    val content = gson.fromJson(note.content, NoteContent::class.java)
                    var imageDriveId: String? = null

                    content.imagePath?.let { path ->
                        val imageFile = createTempFileForUpload(path.toUri())
                        if (imageFile != null) {
                            imageDriveId = googleDriveManager.uploadMediaFile(imageFile, "image/jpeg")
                            imageFile.delete()
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

                    val progress = ((index + 1) * 100) / totalSteps
                    withContext(Dispatchers.Main) {
                        updateProgress(progress)
                    }
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
                    updateProgress(100)
                    dismissProgressDialog()
                    if (success) {
                        Toast.makeText(requireContext(), "Notes and settings backed up successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "An error occurred during backup.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    dismissProgressDialog()
                }
                showError("Backup failed", e)
            }
        }

        @Throws(IOException::class)
        private fun createTempFileForUpload(uri: Uri): File? {
            val context = requireContext()
            return try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val tempFile = File.createTempFile("upload_", ".jpg", context.cacheDir)
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream?.copyTo(outputStream)
                }
                inputStream?.close()
                tempFile
            } catch (e: Exception) {
                Log.e("SettingsFragment", "Error creating temp file from URI", e)
                null
            }
        }

        private fun restoreNotes(googleDriveManager: GoogleDriveManager) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Searching for backups...", Toast.LENGTH_SHORT).show()
                    }

                    val backupFile = googleDriveManager.getBackupFiles()?.firstOrNull()
                    if (backupFile == null) {
                        withContext(Dispatchers.Main) { Toast.makeText(requireContext(), getString(R.string.backup_not_found), Toast.LENGTH_LONG).show() }
                        return@launch
                    }

                    val jsonContent = googleDriveManager.downloadJsonBackup(backupFile.id)
                    if (jsonContent.isNullOrBlank()) {
                        withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "Backup file is empty or corrupt.", Toast.LENGTH_LONG).show() }
                        return@launch
                    }

                    val type = object : TypeToken<BackupData>() {}.type
                    val backupData: BackupData = gson.fromJson(jsonContent, type)

                    withContext(Dispatchers.Main) {
                        if (backupData.passwordHash != null && backupData.salt != null) {
                            showPasswordPromptForRestore(googleDriveManager, backupData)
                        } else {
                            showRestoreConfirmationDialog(googleDriveManager, backupData)
                        }
                    }

                } catch (e: Exception) {
                    showError(getString(R.string.restore_failed), e)
                }
            }
        }

        private fun showRestoreConfirmationDialog(googleDriveManager: GoogleDriveManager, backupData: BackupData) {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.restore_dialog_title))
                .setMessage(getString(R.string.restore_dialog_message))
                .setPositiveButton(getString(R.string.restore_confirm)) { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        proceedWithRestore(googleDriveManager, backupData)
                    }
                }
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .show()
        }


        private fun showPasswordPromptForRestore(googleDriveManager: GoogleDriveManager, backupData: BackupData) {
            val editText = EditText(requireContext()).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                hint = getString(R.string.enter_current_password_hint)
            }

            AlertDialog.Builder(requireContext())
                .setTitle("Password Required")
                .setMessage("This backup is password protected. Please enter your password to continue.")
                .setView(editText)
                .setPositiveButton("Confirm") { _, _ ->
                    val enteredPassword = editText.text.toString()
                    if (backupData.passwordHash != null && backupData.salt != null) {
                        if (PasswordManager.checkPassword(enteredPassword, backupData.salt, backupData.passwordHash)) {
                            lifecycleScope.launch(Dispatchers.IO) {
                                proceedWithRestore(googleDriveManager, backupData)
                            }
                        } else {
                            Toast.makeText(requireContext(), "Incorrect password!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        private suspend fun proceedWithRestore(googleDriveManager: GoogleDriveManager, backupData: BackupData) {
            withContext(Dispatchers.Main) {
                showProgressDialog(R.string.restore_in_progress)
            }

            try {
                PreferenceManager.getDefaultSharedPreferences(requireContext()).edit {
                    putString("theme_selection", backupData.settings.themeSelection)
                    putString("color_selection", backupData.settings.colorSelection)
                    putString("widget_background_selection", backupData.settings.widgetBackgroundSelection)
                }

                val notesFromBackup = backupData.notes
                val restoredNotes = mutableListOf<Note>()
                val totalSteps = notesFromBackup.size + 1

                notesFromBackup.forEachIndexed { index, note ->
                    val content = gson.fromJson(note.content, NoteContent::class.java)
                    var localImagePath: String? = null
                    content.imagePath?.let { driveId ->
                        val imageFile = createImageFile()
                        if (googleDriveManager.downloadMediaFile(driveId, imageFile)) {
                            localImagePath = imageFile.toURI().toString()
                        }
                    }
                    var localAudioPath: String? = null
                    content.audioFilePath?.let { driveId ->
                        val audioFile = createAudioFile()
                        if (googleDriveManager.downloadMediaFile(driveId, audioFile)) {
                            localAudioPath = audioFile.absolutePath
                        }
                    }
                    val finalContent = content.copy(imagePath = localImagePath, audioFilePath = localAudioPath)
                    restoredNotes.add(note.copy(content = gson.toJson(finalContent)))

                    val progress = ((index + 1) * 100) / totalSteps
                    withContext(Dispatchers.Main) {
                        updateProgress(progress)
                    }
                }

                noteDao.deleteAllNotes()
                noteDao.insertAll(restoredNotes)

                if (backupData.passwordHash != null && backupData.salt != null) {
                    PasswordManager.restorePassword(requireContext(), backupData.passwordHash, backupData.salt)
                }

                withContext(Dispatchers.Main) {
                    updateProgress(100)
                    dismissProgressDialog()
                    Toast.makeText(requireContext(), getString(R.string.restore_success), Toast.LENGTH_LONG).show()
                    activity?.recreate()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    dismissProgressDialog()
                }
                showError("Restore operation failed", e)
            }
        }

        @Throws(IOException::class)
        private fun createImageFile(): File {
            val context = requireContext()
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val storageDir: File = context.getExternalFilesDir("RestoredImages") ?: context.filesDir
            storageDir.mkdirs()
            return File.createTempFile("IMG_${timeStamp}_", ".jpg", storageDir)
        }

        @Throws(IOException::class)
        private fun createAudioFile(): File {
            val context = requireContext()
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val storageDir: File = context.getExternalFilesDir("RestoredAudio") ?: context.filesDir
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