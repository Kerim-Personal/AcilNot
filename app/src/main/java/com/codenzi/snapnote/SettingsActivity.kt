package com.codenzi.snapnote

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.gson.Gson
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

        // Modern ActivityResultLauncher, oturum açma sonucunu yönetir.
        private val googleSignInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // Giriş başarılı, yedekleme işlemini tetikle
                result.data?.let { handleSignInSuccess(it) }
            } else {
                Toast.makeText(requireContext(), "Google ile oturum açma iptal edildi.", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            findPreference<Preference>("google_drive_backup")?.setOnPreferenceClickListener {
                // Gerekli izinleri (scope) belirterek oturum açma seçeneklerini yapılandırıyoruz.
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestScopes(Scope("https://www.googleapis.com/auth/drive.appdata"))
                    .build()
                val googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)

                // Her seferinde temiz bir başlangıç için önce oturumu kapatıyoruz.
                googleSignInClient.signOut().addOnCompleteListener {
                    googleSignInLauncher.launch(googleSignInClient.signInIntent)
                }
                true
            }
        }

        private fun handleSignInSuccess(data: Intent) {
            GoogleSignIn.getSignedInAccountFromIntent(data)
                .addOnSuccessListener { account ->
                    // Hesap bilgisi başarıyla alındı, şimdi Access Token'ı alacağız.
                    getAccessTokenAndBackup(account)
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Oturum açma hatası: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
        }

        private fun getAccessTokenAndBackup(account: GoogleSignInAccount) {
            lifecycleScope.launch {
                try {
                    // Access Token'ı IO thread üzerinde alıyoruz.
                    val accessToken = withContext(Dispatchers.IO) {
                        val scope = "oauth2:https://www.googleapis.com/auth/drive.appdata"
                        // Bu, access token almanın doğru ve stabil yöntemidir.
                        GoogleAuthUtil.getToken(requireContext(), account.account!!, scope)
                    }

                    // Yedekleme işlemini başlatıyoruz.
                    Toast.makeText(requireContext(), "Erişim anahtarı alındı. Yedekleme başlatılıyor...", Toast.LENGTH_SHORT).show()

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
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Yedekleme başarısız: ${e.message}", Toast.LENGTH_LONG).show()
                        e.printStackTrace()
                    }
                }
            }
        }
    }
}