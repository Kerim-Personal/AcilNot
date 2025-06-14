package com.example.acilnotuygulamasi

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class NoteActivity : AppCompatActivity() {

    private lateinit var noteDao: NoteDao
    private var currentNoteId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note)
        window.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        noteDao = NoteDatabase.getDatabase(this).noteDao()
        val noteInput: EditText = findViewById(R.id.et_note_input)
        val saveButton: Button = findViewById(R.id.btn_save_note)

        if (intent.hasExtra("NOTE_ID")) {
            currentNoteId = intent.getIntExtra("NOTE_ID", 0)
            this.title = "Notu Düzenle"
            lifecycleScope.launch {
                val note = noteDao.getNoteById(currentNoteId!!)
                note?.let {
                    noteInput.setText(it.content)
                }
            }
        } else {
            this.title = "Yeni Not Ekle"
        }

        saveButton.setOnClickListener {
            val noteText = noteInput.text.toString()
            if (noteText.isBlank()) {
                Toast.makeText(this, "Not boş olamaz!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                if (currentNoteId == null) {
                    noteDao.insert(Note(content = noteText))
                } else {
                    val updatedNote = Note(id = currentNoteId!!, content = noteText)
                    noteDao.update(updatedNote)
                }
                // Widget'ı doğru yöntemle güncelle
                updateAllWidgets()
                finish()
            }
        }
    }

    // BU FONKSİYON DÜZELTİLDİ
    private fun updateAllWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, NoteWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        // Bu komut, widget'ın listesini sağlayan Factory'yi yeniden çalıştırır.
        // Bu, widget'ı güncellemenin doğru ve en güvenli yoludur.
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.lv_widget_notes)
    }
}