package com.codenzi.snapnote

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.net.toUri
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

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            findPreference<ListPreference>("theme_selection")?.setOnPreferenceChangeListener { _, newValue ->
                val mode = when (newValue.toString()) {
                    "light" -> AppCompatDelegate.MODE_NIGHT_NO
                    "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                AppCompatDelegate.setDefaultNightMode(mode)
                true
            }

            findPreference<Preference>("password_settings")?.setOnPreferenceClickListener {
                startActivity(Intent(activity, PasswordSettingsActivity::class.java))
                true
            }

            findPreference<Preference>("trash_settings")?.setOnPreferenceClickListener {
                startActivity(Intent(activity, TrashActivity::class.java))
                true
            }

            findPreference<ListPreference>("widget_background_selection")?.setOnPreferenceChangeListener { _, _ ->
                // Değişikliğin SharedPreferences'a yazılmasını beklemek için küçük bir gecikme ekliyoruz.
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    updateAllWidgets()
                }, 100)
                true
            }

            findPreference<Preference>("privacy_policy")?.setOnPreferenceClickListener {
                val url = "https://www.codenzi.com"
                try {
                    // DÜZELTME: KTX uzantı fonksiyonu kullanıldı.
                    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                    startActivity(intent)
                } catch (e: Exception) {
                    showErrorDialog(R.string.toast_no_browser_found)
                }
                true
            }

            findPreference<Preference>("contact_us")?.setOnPreferenceClickListener {
                val email = "info@codenzi.com"
                val subject = getString(R.string.contact_us_email_subject)
                try {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        // DÜZELTME: KTX uzantı fonksiyonu kullanıldı.
                        data = "mailto:".toUri()
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                        putExtra(Intent.EXTRA_SUBJECT, subject)
                    }
                    startActivity(Intent.createChooser(intent, getString(R.string.contact_us_email_chooser_title)))
                } catch (e: Exception) {
                    showErrorDialog(R.string.toast_no_email_app_found)
                }
                true
            }
        }

        private fun updateAllWidgets() {
            try {
                val context = requireContext()
                val appWidgetManager = AppWidgetManager.getInstance(context)

                // Tüm widget türlerini tek bir listede toplayıp döngüye alarak kodu daha temiz hale getirelim.
                val widgetProviders = listOf(
                    NoteWidgetProvider::class.java,
                    VoiceMemoWidgetProvider::class.java,
                    CameraWidgetProvider::class.java
                )

                widgetProviders.forEach { providerClass ->
                    val componentName = ComponentName(context, providerClass)
                    val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                    if (appWidgetIds.isNotEmpty()) {
                        val updateIntent = Intent(context, providerClass).apply {
                            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                        }
                        context.sendBroadcast(updateIntent)
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun showErrorDialog(messageResId: Int) {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.error_dialog_title))
                .setMessage(getString(messageResId))
                .setPositiveButton(getString(R.string.dialog_ok), null)
                .show()
        }
    }
}