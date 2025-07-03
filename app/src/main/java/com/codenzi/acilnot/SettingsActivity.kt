package com.codenzi.acilnot

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Düzeltme: settings_activity yerine activity_settings kullanıldı
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

    // Ayarları gösterecek olan Fragment
    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            // preferences.xml dosyasını yükle
            setPreferencesFromResource(R.xml.preferences, rootKey)

            // Tema tercihini dinle ve uygula
            val themePreference: androidx.preference.ListPreference? = findPreference("theme_selection")
            themePreference?.setOnPreferenceChangeListener { _, newValue ->
                val mode = when (newValue.toString()) {
                    "light" -> AppCompatDelegate.MODE_NIGHT_NO
                    "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                AppCompatDelegate.setDefaultNightMode(mode)
                true
            }

            // Çöp Kutusu tercihini bul ve tıklama dinleyicisi ekle
            val trashPreference = findPreference<androidx.preference.Preference>("trash_settings") //
            trashPreference?.setOnPreferenceClickListener { //
                val intent = Intent(context, TrashActivity::class.java) //
                startActivity(intent) //
                true //
            }
        }
    }
}