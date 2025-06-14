
package com.example.acilnotuygulamasi
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class NoteActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note)

        // Activity'yi diyalog gibi göstermek için pencere boyutunu ayarla
        window.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(), // Genişlik %90
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT // Yükseklik içeriğe göre
        )
        this.title = "Notu Düzenle"


        val noteInput: EditText = findViewById(R.id.et_note_input)
        val saveButton: Button = findViewById(R.id.btn_save_note)

        // Mevcut notu EditText'e yükle
        noteInput.setText(NoteStorage.loadNote(this))

        saveButton.setOnClickListener {
            val newNote = noteInput.text.toString()
            // Notu kaydet
            NoteStorage.saveNote(this, newNote)

            // Widget'ı güncellemek için bir broadcast gönder
            updateAllWidgets()

            // Activity'yi kapat
            finish()
        }
    }

    private fun updateAllWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, NoteWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

        for (appWidgetId in appWidgetIds) {
            NoteWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId)
        }
    }
}