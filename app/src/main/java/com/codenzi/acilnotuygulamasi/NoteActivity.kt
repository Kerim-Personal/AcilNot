package com.codenzi.acilnotuygulamasi

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

class NoteActivity : AppCompatActivity() {

    private lateinit var noteDao: NoteDao
    private var currentNoteId: Int? = null

    private lateinit var noteInput: EditText
    private lateinit var saveButton: Button
    private lateinit var deleteButton: Button
    private lateinit var editHistoryText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note)

        noteDao = NoteDatabase.getDatabase(this).noteDao()
        noteInput = findViewById(R.id.et_note_input)
        saveButton = findViewById(R.id.btn_save_note)
        deleteButton = findViewById(R.id.btn_delete_note)
        editHistoryText = findViewById(R.id.tv_edit_history)

        // Eğer ID varsa, bu bir düzenlemedir. Notu yükle ve Sil butonunu göster.
        if (intent.hasExtra("NOTE_ID")) {
            currentNoteId = intent.getIntExtra("NOTE_ID", 0)
            this.title = "Notu Düzenle"
            deleteButton.visibility = View.VISIBLE // Sil butonunu görünür yap
            loadNote()
        } else {
            // ID yoksa bu yeni nottur. Sil butonu gizli kalsın.
            this.title = "Yeni Not Ekle"
            editHistoryText.visibility = View.GONE
        }

        saveButton.setOnClickListener { saveNote() }
        deleteButton.setOnClickListener { showDeleteConfirmationDialog() }
    }

    private fun loadNote() {
        lifecycleScope.launch {
            val note = noteDao.getNoteById(currentNoteId!!)
            note?.let {
                displayEditHistory(it)
                noteInput.setText(it.content)
            }
        }
    }

    private fun saveNote() {
        val noteText = noteInput.text.toString()
        if (noteText.isBlank()) {
            Toast.makeText(this, "Not boş olamaz!", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            if (currentNoteId == null) {
                noteDao.insert(Note(content = noteText, createdAt = System.currentTimeMillis()))
            } else {
                val existingNote = noteDao.getNoteById(currentNoteId!!)
                existingNote?.let {
                    val updatedModifications = it.modifiedAt.toMutableList().apply {
                        add(System.currentTimeMillis())
                    }
                    val updatedNote = it.copy(
                        content = noteText,
                        modifiedAt = updatedModifications
                    )
                    noteDao.update(updatedNote)
                }
            }
            updateAllWidgets()
            finish()
        }
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Notu Sil")
            .setMessage("Bu notu silmek istediğinizden emin misiniz?")
            .setPositiveButton("Evet, Sil") { _, _ -> deleteNote() }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun deleteNote() {
        currentNoteId?.let { id ->
            lifecycleScope.launch {
                noteDao.deleteById(id)
                updateAllWidgets()
                Toast.makeText(applicationContext, "Not silindi.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun updateAllWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, NoteWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        appWidgetIds.forEach { appWidgetId ->
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.lv_widget_notes)
        }
    }

    private fun displayEditHistory(note: Note) {
        val historyBuilder = StringBuilder()
        historyBuilder.append("Oluşturulma: ${formatDate(note.createdAt)}")

        if (note.modifiedAt.isNotEmpty()) {
            historyBuilder.append("\n\nDüzenleme Geçmişi:")
            note.modifiedAt.forEach { timestamp ->
                historyBuilder.append("\n- ${formatDate(timestamp)}")
            }
        }
        editHistoryText.text = historyBuilder.toString()
    }
    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}