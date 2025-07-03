package com.codenzi.acilnot

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.os.Bundle
import android.text.Html
import android.text.Spannable
import android.text.method.ScrollingMovementMethod
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import android.widget.ScrollView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle // Bu import NoteActivity'de doğrudan kullanılmadığı için IDE uyarısı verebilir, dilerseniz kaldırabilirsiniz.
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")
class NoteActivity : AppCompatActivity() {

    private lateinit var noteDao: NoteDao
    private var currentNoteId: Int? = null

    private lateinit var noteTitle: EditText
    private lateinit var noteInput: EditText
    private lateinit var saveButton: Button
    private lateinit var deleteButton: Button
    private lateinit var editHistoryText: TextView

    private lateinit var boldButton: Button
    private lateinit var italicButton: Button
    private lateinit var strikethroughButton: Button
    private lateinit var showHistoryButton: ImageButton

    private lateinit var colorPickers: List<View>
    private var selectedColor: String = "#FFECEFF1"

    private lateinit var checklistRecyclerView: RecyclerView
    private lateinit var addChecklistItemButton: Button
    private lateinit var checklistAdapter: ChecklistItemAdapter
    private var checklistItems = mutableListOf<ChecklistItem>()

    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note)

        noteDao = NoteDatabase.getDatabase(this).noteDao()
        noteTitle = findViewById(R.id.et_note_title)
        noteInput = findViewById(R.id.et_note_input)
        saveButton = findViewById(R.id.btn_save_note)
        deleteButton = findViewById(R.id.btn_delete_note)
        editHistoryText = findViewById(R.id.tv_edit_history)
        editHistoryText.movementMethod = ScrollingMovementMethod.getInstance()

        showHistoryButton = findViewById(R.id.btn_show_history)

        // Bilgi butonunu mor yap
        val purpleColor = ContextCompat.getColor(this, R.color.purple_500)
        showHistoryButton.setColorFilter(purpleColor, PorterDuff.Mode.SRC_IN)

        // Hata düzeltmesi: Biçimlendirme butonları başlatılıyor
        boldButton = findViewById(R.id.btn_bold)
        italicButton = findViewById(R.id.btn_italic)
        strikethroughButton = findViewById(R.id.btn_strikethrough)

        // Bilgi butonuna tıklama dinleyicisi
        showHistoryButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Düzenleme Geçmişi")
                .setMessage(editHistoryText.text)
                .setPositiveButton("Tamam", null)
                .show()
        }

        checklistRecyclerView = findViewById(R.id.rv_checklist)
        addChecklistItemButton = findViewById(R.id.btn_add_checklist_item)
        setupChecklist()

        setupFormattingButtons()
        setupColorPickers()

        processIntent(intent)

        saveButton.setOnClickListener { saveNote() }
        deleteButton.setOnClickListener { showDeleteConfirmationDialog() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processIntent(intent)
    }

    private fun processIntent(intent: Intent) {
        if (intent.hasExtra("NOTE_ID")) {
            currentNoteId = intent.getIntExtra("NOTE_ID", 0)
            this.title = "Notu Düzenle"
            deleteButton.visibility = View.VISIBLE
            loadNote()
        } else {
            currentNoteId = null
            this.title = "Yeni Not Ekle"
            deleteButton.visibility = View.GONE
            updateColorSelection(findViewById(R.id.color_default))
            updateWindowBackground()
            noteTitle.text.clear()
            noteInput.text.clear()

            // Verimsiz notifyDataSetChanged() yerine daha spesifik metod kullanılıyor
            val oldSize = checklistItems.size
            if (oldSize > 0) {
                checklistItems.clear()
                checklistAdapter.notifyItemRangeRemoved(0, oldSize)
            }
        }
        // Not yoksa veya yeni notsa bilgi butonunu gizle
        showHistoryButton.visibility = if (currentNoteId != null) View.VISIBLE else View.GONE
    }

    private fun setupChecklist() {
        checklistAdapter = ChecklistItemAdapter(checklistItems)
        checklistRecyclerView.adapter = checklistAdapter
        checklistRecyclerView.layoutManager = LinearLayoutManager(this)

        addChecklistItemButton.setOnClickListener {
            checklistAdapter.addItem()
        }
    }

    private fun setupFormattingButtons() {
        boldButton.setOnClickListener { applySpan(Typeface.BOLD) }
        italicButton.setOnClickListener { applySpan(Typeface.ITALIC) }
        strikethroughButton.setOnClickListener { applySpan(-1) } // -1 üstü çizili için bir işaretçi olarak kullanılıyor
    }

    // applySpan metodunun güncellenmiş hali: Biçimlendirmeyi seçili metin üzerinde açıp kapatma (toggle) yeteneği eklendi
    private fun applySpan(spanType: Int) {
        val start = noteInput.selectionStart
        val end = noteInput.selectionEnd

        if (start == end) {
            // Seçili metin yoksa şimdilik bir işlem yapma.
            // Buraya, gelecekteki yazma için varsayılan stilin değiştirilmesi gibi daha karmaşık bir mantık eklenebilir.
            return
        }

        val spannable = noteInput.text as Spannable

        var targetSpanToAdd: Any? = null
        var currentStyleAppliedToSelection = false

        when (spanType) {
            Typeface.BOLD -> {
                val boldSpans = spannable.getSpans(start, end, StyleSpan::class.java)
                    .filter { it.style == Typeface.BOLD }
                currentStyleAppliedToSelection = boldSpans.isNotEmpty() && boldSpans.all {
                    spannable.getSpanStart(it) <= start && spannable.getSpanEnd(it) >= end
                }
                targetSpanToAdd = StyleSpan(Typeface.BOLD)
            }
            Typeface.ITALIC -> {
                val italicSpans = spannable.getSpans(start, end, StyleSpan::class.java)
                    .filter { it.style == Typeface.ITALIC }
                currentStyleAppliedToSelection = italicSpans.isNotEmpty() && italicSpans.all {
                    spannable.getSpanStart(it) <= start && spannable.getSpanEnd(it) >= end
                }
                targetSpanToAdd = StyleSpan(Typeface.ITALIC)
            }
            -1 -> { // Üstü çizili
                val strikethroughSpans = spannable.getSpans(start, end, StrikethroughSpan::class.java)
                currentStyleAppliedToSelection = strikethroughSpans.isNotEmpty() && strikethroughSpans.all {
                    spannable.getSpanStart(it) <= start && spannable.getSpanEnd(it) >= end
                }
                targetSpanToAdd = StrikethroughSpan()
            }
        }

        if (currentStyleAppliedToSelection) {
            // Stil zaten tüm seçime uygulanmışsa, kaldır
            // SpansToRemove'u tek bir List<Any> tipine dönüştürerek tip çıkarım hatasını giderdik.
            val spansToRemove = mutableListOf<Any>()
            when (spanType) {
                Typeface.BOLD -> {
                    spannable.getSpans(start, end, StyleSpan::class.java)
                        .filter { it.style == Typeface.BOLD }
                        .forEach { spansToRemove.add(it) }
                }
                Typeface.ITALIC -> {
                    spannable.getSpans(start, end, StyleSpan::class.java)
                        .filter { it.style == Typeface.ITALIC }
                        .forEach { spansToRemove.add(it) }
                }
                -1 -> { // Strikethrough
                    spannable.getSpans(start, end, StrikethroughSpan::class.java)
                        .forEach { spansToRemove.add(it) }
                }
            }
            spansToRemove.forEach { spannable.removeSpan(it) }
        } else if (targetSpanToAdd != null) {
            // Stil tüm seçime uygulanmamışsa, uygula
            spannable.setSpan(targetSpanToAdd, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        // Spannable üzerinde yapılan değişikliklerden sonra EditText'i yenilemek ve seçimi geri yüklemek önemlidir.
        noteInput.setText(spannable, TextView.BufferType.SPANNABLE)
        noteInput.setSelection(start, end)
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
        // 'blue', 'green', 'pink' referans hataları düzeltildi.
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
            val note = currentNoteId?.let { noteDao.getNoteById(it) }
            note?.let {
                // Kod kalite uyarısı giderildi, 'ifBlank' kullanıldı.
                this@NoteActivity.title = it.title.ifBlank { "Notu Düzenle" }
                noteTitle.setText(it.title)

                displayEditHistory(it)
                try {
                    val content = gson.fromJson(it.content, NoteContent::class.java)
                    noteInput.setText(Html.fromHtml(content.text, Html.FROM_HTML_MODE_LEGACY))

                    // Verimsiz notifyDataSetChanged() yerine daha spesifik metodlar kullanılıyor
                    val oldSize = checklistItems.size
                    checklistItems.clear()
                    checklistAdapter.notifyItemRangeRemoved(0, oldSize)
                    checklistItems.addAll(content.checklist)
                    checklistAdapter.notifyItemRangeInserted(0, content.checklist.size)

                } catch (e: JsonSyntaxException) {
                    noteInput.setText(Html.fromHtml(it.content, Html.FROM_HTML_MODE_LEGACY))

                    // Verimsiz notifyDataSetChanged() yerine daha spesifik metodlar kullanılıyor
                    val oldSize = checklistItems.size
                    if (oldSize > 0) {
                        checklistItems.clear()
                        checklistAdapter.notifyItemRangeRemoved(0, oldSize)
                    }
                }

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
        val titleText = noteTitle.text.toString().trim()
        val noteText = Html.toHtml(noteInput.text, Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)

        if (titleText.isBlank() && noteInput.text.isBlank() && checklistItems.all { it.text.isBlank() }) {
            Toast.makeText(this, "Not içeriği boş olamaz!", Toast.LENGTH_SHORT).show()
            return
        }

        val noteContent = NoteContent(text = noteText, checklist = checklistItems)
        val jsonContent = gson.toJson(noteContent)

        lifecycleScope.launch {
            if (currentNoteId == null) {
                val newNote = Note(title = titleText, content = jsonContent, createdAt = System.currentTimeMillis(), color = selectedColor)
                noteDao.insert(newNote)
            } else {
                val existingNote = noteDao.getNoteById(currentNoteId!!)
                existingNote?.let {
                    val updatedModifications = it.modifiedAt.toMutableList().apply {
                        add(System.currentTimeMillis())
                    }
                    val updatedNote = it.copy(
                        title = titleText,
                        content = jsonContent,
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
            .setTitle(getString(R.string.delete_note_confirmation_title))
            .setMessage(getString(R.string.delete_note_to_trash_confirmation_message))
            .setPositiveButton(getString(R.string.dialog_move_to_trash)) { _, _ -> deleteNote() }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    private fun deleteNote() {
        currentNoteId?.let { id ->
            lifecycleScope.launch {
                // Notu kalıcı olarak silmek yerine çöp kutusuna taşı
                noteDao.softDeleteById(id, System.currentTimeMillis())
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