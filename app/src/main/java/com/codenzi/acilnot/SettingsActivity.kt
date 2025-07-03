package com.codenzi.acilnot

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.preference.PreferenceFragmentCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import android.content.res.Configuration // Bu satırı ekleyin

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

            // Mevcut tema modunu belirle (aydınlık mı karanlık mı?)
            val isDarkMode = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES ||
                    (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM &&
                            (context?.resources?.configuration?.uiMode?.and(Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES))

            // İkon rengini belirle
            val iconColor = if (isDarkMode) ContextCompat.getColor(requireContext(), android.R.color.white)
            else ContextCompat.getColor(requireContext(), android.R.color.black)

            // Tema tercihini bul ve ikonunu ayarla
            val themePreference: androidx.preference.ListPreference? = findPreference("theme_selection")
            themePreference?.let {
                val iconDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_settings_24)
                iconDrawable?.let { drawable ->
                    val wrappedDrawable = DrawableCompat.wrap(drawable)
                    DrawableCompat.setTint(wrappedDrawable, iconColor)
                    it.icon = wrappedDrawable
                }
            }

            // Çöp Kutusu tercihini bul ve ikonunu ayarla
            val trashPreference = findPreference<androidx.preference.Preference>("trash_settings")
            trashPreference?.let {
                val iconDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.outline_archive_24)
                iconDrawable?.let { drawable ->
                    val wrappedDrawable = DrawableCompat.wrap(drawable)
                    DrawableCompat.setTint(wrappedDrawable, iconColor)
                    it.icon = wrappedDrawable
                }
            }

            // Tema tercihini dinle ve uygula
            val themePreferenceListener: androidx.preference.ListPreference? = findPreference("theme_selection")
            themePreferenceListener?.setOnPreferenceChangeListener { _, newValue ->
                val mode = when (newValue.toString()) {
                    "light" -> AppCompatDelegate.MODE_NIGHT_NO
                    "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                AppCompatDelegate.setDefaultNightMode(mode)
                true
            }

            // Çöp Kutusu tercihini bul ve tıklama dinleyicisi ekle
            val trashPreferenceClickListener = findPreference<androidx.preference.Preference>("trash_settings")
            trashPreferenceClickListener?.setOnPreferenceClickListener {
                val intent = Intent(context, TrashActivity::class.java)
                startActivity(intent)
                true
            }
        }
    }
}