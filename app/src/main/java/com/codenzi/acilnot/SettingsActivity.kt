package com.codenzi.acilnot

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar: Toolbar = findViewById(R.id.settings_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Ayarlar"

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

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            // TEMA DEĞİŞTİRME ÖZELLİĞİ DÜZELTİLDİ
            val themePreference: ListPreference? = findPreference("theme_selection")
            themePreference?.setOnPreferenceChangeListener { _, newValue ->
                val mode = when (newValue.toString()) {
                    "light" -> AppCompatDelegate.MODE_NIGHT_NO
                    "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                AppCompatDelegate.setDefaultNightMode(mode)
                true
            }

            // Parola Ayarları butonu
            val passwordSettingsPreference: Preference? = findPreference("password_settings")
            passwordSettingsPreference?.setOnPreferenceClickListener {
                startActivity(Intent(activity, PasswordSettingsActivity::class.java))
                true
            }

            // Çöp Kutusu butonu
            val trashPreference: Preference? = findPreference("trash_settings")
            trashPreference?.setOnPreferenceClickListener {
                startActivity(Intent(activity, TrashActivity::class.java))
                true
            }

            // Gizlilik Sözleşmesi butonu
            val privacyPolicyPreference: Preference? = findPreference("privacy_policy")
            privacyPolicyPreference?.setOnPreferenceClickListener {
                val url = "https://www.codenzi.com"
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                } catch (e: Exception) {
                    AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.error_dialog_title))
                        .setMessage(getString(R.string.toast_no_browser_found))
                        .setPositiveButton(getString(R.string.dialog_ok), null)
                        .show()
                }
                true
            }


            val contactUsPreference: Preference? = findPreference("contact_us")
            contactUsPreference?.setOnPreferenceClickListener {
                val email = "info@codenzi.com"
                val subject = getString(R.string.contact_us_email_subject)
                try {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:")
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                        putExtra(Intent.EXTRA_SUBJECT, subject)
                    }
                    startActivity(Intent.createChooser(intent, getString(R.string.contact_us_email_chooser_title)))
                } catch (e: Exception) {
                    AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.error_dialog_title))
                        .setMessage(getString(R.string.toast_no_email_app_found))
                        .setPositiveButton(getString(R.string.dialog_ok), null)
                        .show()
                }
                true
            }
        }
    }
}