package com.codenzi.acilnot

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
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
                // TODO: "your_privacy_policy_url" kısmını kendi gizlilik politikası linkinizle değiştirin
                val url = "https://www.your_privacy_policy_url.com"
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Web tarayıcısı bulunamadı.", Toast.LENGTH_SHORT).show()
                }
                true
            }

            // Bize Ulaşın butonu
            val contactUsPreference: Preference? = findPreference("contact_us")
            contactUsPreference?.setOnPreferenceClickListener {
                // TODO: "your_email@example.com" kısmını kendi destek e-posta adresinizle değiştirin
                val email = "destek@example.com"
                val subject = "Acil Not Uygulaması Geri Bildirim"
                try {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:")
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                        putExtra(Intent.EXTRA_SUBJECT, subject)
                    }
                    startActivity(Intent.createChooser(intent, "E-posta gönder..."))
                } catch (e: Exception) {
                    Toast.makeText(context, "E-posta uygulaması bulunamadı.", Toast.LENGTH_SHORT).show()
                }
                true
            }
        }
    }
}