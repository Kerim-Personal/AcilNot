package com.codenzi.acilnot

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Color
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
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

    private lateinit var colorPickers: List<View>
    private var selectedColor: String = "#FFECEFF1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note)

        noteDao = NoteDatabase.getDatabase(this).noteDao()
        noteInput = findViewById(R.id.et_note_input)
        saveButton = findViewById(R.id.btn_save_note)
        deleteButton = findViewById(R.id.btn_delete_note)
        editHistoryText = findViewById(R.id.tv_edit_history)

        editHistoryText.movementMethod = ScrollingMovementMethod()

        setupColorPickers()

        if (intent.hasExtra("NOTE_ID")) {
            currentNoteId = intent.getIntExtra("NOTE_ID", 0)
            this.title = "Notu Düzenle"
            deleteButton.visibility = View.VISIBLE
            loadNote()
        } else {
            this.title = "Yeni Not Ekle"
            editHistoryText.visibility = View.GONE
            updateColorSelection(findViewById(R.id.color_default))
            updateWindowBackground()
        }

        saveButton.setOnClickListener { saveNote() }
        deleteButton.setOnClickListener { showDeleteConfirmationDialog() }
    }

    private fun setupColorPickers() {
        val colorDefault: FrameLayout = findViewById(R.id.color_default)
        val colorYellow: FrameLayout = findViewById(R.id.color_yellow)
        val colorBlue: FrameLayout = findViewById(R.id.color_blue)
        val colorGreen: FrameLayout = findViewById(R.id.color_green)
        val colorPink: FrameLayout = findViewById(R.id.color_pink)

        colorPickers = listOf(colorDefault, colorYellow, colorBlue, colorGreen, colorPink)

        colorDefault.setOnClickListener { onColorSelected(it, R.color.note_color_default) }
        colorYellow.setOnClickListener { onColorSelected(it, R.color.note_color_yellow) }
        colorBlue.setOnClickListener { onColorSelected(it, R.color.note_color_blue) }
        colorGreen.setOnClickListener { onColorSelected(it, R.color.note_color_green) }
        colorPink.setOnClickListener { onColorSelected(it, R.color.note_color_pink) }
    }

    private fun onColorSelected(view: View, colorResId: Int) {
        selectedColor = String.format("#%08X", ContextCompat.getColor(this, colorResId))
        updateColorSelection(view)
        updateWindowBackground()
    }

    private fun updateColorSelection(selectedView: View?) {
        colorPickers.forEach { picker ->
            picker.isSelected = (picker == selectedView)
        }
    }

    private fun loadNote() {
        lifecycleScope.launch {
            val note = noteDao.getNoteById(currentNoteId!!)
            note?.let {
                displayEditHistory(it)
                noteInput.setText(it.content)
                selectedColor = it.color
                updateWindowBackground()

                val colorInt = try { it.color.toColorInt() } catch (e: Exception) { Color.WHITE }
                val viewToSelect: View = when(colorInt) {
                    ContextCompat.getColor(this@NoteActivity, R.color.note_color_yellow) -> findViewById(R.id.color_yellow)
                    ContextCompat.getColor(this@NoteActivity, R.color.note_color_blue) -> findViewById(R.id.color_blue)
                    ContextCompat.getColor(this@NoteActivity, R.color.note_color_green) -> findViewById(R.id.color_green)
                    ContextCompat.getColor(this@NoteActivity, R.color.note_color_pink) -> findViewById(R.id.color_pink)
                    else -> findViewById(R.id.color_default)
                }
                updateColorSelection(viewToSelect)
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
                val newNote = Note(content = noteText, createdAt = System.currentTimeMillis(), color = selectedColor)
                noteDao.insert(newNote)
            } else {
                val existingNote = noteDao.getNoteById(currentNoteId!!)
                existingNote?.let {
                    val updatedModifications = it.modifiedAt.toMutableList().apply {
                        add(System.currentTimeMillis())
                    }
                    val updatedNote = it.copy(
                        content = noteText,
                        modifiedAt = updatedModifications,
                        color = selectedColor
                    )
                    noteDao.update(updatedNote)
                }
            }
            updateAllWidgets()
            finish()
        }
    }

    private fun updateWindowBackground() {
        try {
            window.setBackgroundDrawable(selectedColor.toColorInt().toDrawable())
        } catch (e: IllegalArgumentException) {
            window.setBackgroundDrawable(Color.WHITE.toDrawable())
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