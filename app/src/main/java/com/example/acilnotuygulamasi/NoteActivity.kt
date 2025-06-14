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

        // Düzenleme için mi yoksa yeni not için mi açıldığını kontrol et
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
                    // Yeni notu ekle
                    noteDao.insert(Note(content = noteText))
                } else {
                    // Mevcut notu güncelle
                    val updatedNote = Note(id = currentNoteId!!, content = noteText)
                    noteDao.update(updatedNote)
                }
                updateAllWidgets()
                finish()
            }
        }
    }

    private fun updateAllWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, NoteWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

        lifecycleScope.launch {
            for (appWidgetId in appWidgetIds) {
                NoteWidgetProvider.updateAppWidget(this@NoteActivity, appWidgetManager, appWidgetId)
            }
        }
    }
}