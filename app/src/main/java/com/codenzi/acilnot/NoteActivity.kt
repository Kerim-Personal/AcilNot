package com.codenzi.acilnot

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
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

    private lateinit var noteTitle: TextInputEditText
    private lateinit var noteInput: SelectionAwareEditText
    private lateinit var saveButton: Button
    private lateinit var deleteButton: Button
    private lateinit var editHistoryText: TextView

    private lateinit var formatToggleButtonGroup: MaterialButtonToggleGroup
    private lateinit var boldButton: MaterialButton
    private lateinit var italicButton: MaterialButton
    private lateinit var strikethroughButton: MaterialButton

    private lateinit var showHistoryButton: ImageButton
    private lateinit var voiceNoteButton: ImageButton
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechRecognizerIntent: Intent

    private lateinit var colorPickers: List<View>
    private var selectedColor: String = "#FFECEFF1"

    private lateinit var checklistRecyclerView: RecyclerView
    private lateinit var addChecklistItemButton: Button
    private lateinit var checklistAdapter: ChecklistItemAdapter
    private var checklistItems = mutableListOf<ChecklistItem>()

    private val gson = Gson()
    private var isUpdatingToggleButtons = false

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startSpeechToText()
            } else {
                Toast.makeText(this, "Mikrofon izni gerekli.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note)

        noteDao = NoteDatabase.getDatabase(this).noteDao()
        noteTitle = findViewById(R.id.et_note_title)
        noteInput = findViewById(R.id.et_note_input)
        saveButton = findViewById(R.id.btn_save_note)
        deleteButton = findViewById(R.id.btn_delete_note)
        editHistoryText = findViewById(R.id.tv_edit_history)
        showHistoryButton = findViewById(R.id.btn_show_history)
        voiceNoteButton = findViewById(R.id.btn_voice_note)
        checklistRecyclerView = findViewById(R.id.rv_checklist)
        addChecklistItemButton = findViewById(R.id.btn_add_checklist_item)

        formatToggleButtonGroup = findViewById(R.id.toggle_button_group)
        boldButton = findViewById(R.id.btn_bold)
        italicButton = findViewById(R.id.btn_italic)
        strikethroughButton = findViewById(R.id.btn_strikethrough)

        setupListeners()
        setupChecklist()
        setupColorPickers()
        setupVoiceNote()

        processIntent(intent)
    }

    private fun setupListeners() {
        showHistoryButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Düzenleme Geçmişi")
                .setMessage(editHistoryText.text)
                .setPositiveButton("Tamam", null)
                .show()
        }

        boldButton.setOnClickListener { toggleStyle(Typeface.BOLD) }
        italicButton.setOnClickListener { toggleStyle(Typeface.ITALIC) }
        strikethroughButton.setOnClickListener { toggleStyle(-1) }


        noteInput.setOnSelectionChangedListener { _, _ ->
            updateFormattingButtonsState()
        }

        saveButton.setOnClickListener { saveNote() }
        deleteButton.setOnClickListener { showDeleteConfirmationDialog() }
    }

    // Orijinal, istediğiniz gibi çalışan toggleStyle fonksiyonu
    private fun toggleStyle(styleType: Int) {
        val spannable = noteInput.text as SpannableStringBuilder
        val start = noteInput.selectionStart
        val end = noteInput.selectionEnd

        val (spanClass, newSpan) = when (styleType) {
            Typeface.BOLD -> StyleSpan::class.java to StyleSpan(Typeface.BOLD)
            Typeface.ITALIC -> StyleSpan::class.java to StyleSpan(Typeface.ITALIC)
            -1 -> StrikethroughSpan::class.java to StrikethroughSpan()
            else -> return
        }

        if (start != end) {
            val existingSpans = spannable.getSpans(start, end, spanClass)
            val styleExists = existingSpans.any {
                (it is StyleSpan && newSpan is StyleSpan && it.style == newSpan.style) || it is StrikethroughSpan
            }

            if (styleExists) {
                existingSpans.forEach {
                    if ((it is StyleSpan && newSpan is StyleSpan && it.style == newSpan.style) || it is StrikethroughSpan) {
                        spannable.removeSpan(it)
                    }
                }
            } else {
                spannable.setSpan(newSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        else {
            val position = noteInput.selectionStart
            val activeSpans = spannable.getSpans(position, position, Any::class.java)

            val activeStyleSpan = activeSpans.find {
                val isMatchingStyle = (it is StyleSpan && newSpan is StyleSpan && it.style == newSpan.style) || it is StrikethroughSpan
                isMatchingStyle && spannable.getSpanFlags(it) == Spanned.SPAN_INCLUSIVE_INCLUSIVE
            }

            if (activeStyleSpan != null) {
                val spanStart = spannable.getSpanStart(activeStyleSpan)
                spannable.removeSpan(activeStyleSpan)
                if (position > spanStart) {
                    spannable.setSpan(newSpan, spanStart, position, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            else {
                spannable.setSpan(newSpan, position, position, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
            }
        }
        updateFormattingButtonsState()
    }

    // *** DÜZELTME: Buton renginin anında güncellenmesini sağlayan YENİ fonksiyon ***
    private fun updateFormattingButtonsState() {
        isUpdatingToggleButtons = true // Döngüleri engellemek için

        val spannable = noteInput.text ?: return
        val position = noteInput.selectionStart
        val selectionEnd = noteInput.selectionEnd

        // Eğer metin seçiliyse, seçimin stil durumuna bak
        if (position != selectionEnd) {
            val boldSpans = spannable.getSpans(position, selectionEnd, StyleSpan::class.java)
            boldButton.isChecked = boldSpans.any { it.style == Typeface.BOLD }

            val italicSpans = spannable.getSpans(position, selectionEnd, StyleSpan::class.java)
            italicButton.isChecked = italicSpans.any { it.style == Typeface.ITALIC }

            val strikeSpans = spannable.getSpans(position, selectionEnd, StrikethroughSpan::class.java)
            strikethroughButton.isChecked = strikeSpans.isNotEmpty()
        } else {
            // Metin seçili değilse, SADECE imlecin olduğu yerdeki "yazım stili" durumuna bak.
            // Bu, bir stili kapattığınızda butonun renginin anında normale dönmesini sağlar.
            val spansAtCursor = spannable.getSpans(position, position, Any::class.java)

            boldButton.isChecked = spansAtCursor.any {
                it is StyleSpan && it.style == Typeface.BOLD && spannable.getSpanFlags(it) == Spanned.SPAN_INCLUSIVE_INCLUSIVE
            }
            italicButton.isChecked = spansAtCursor.any {
                it is StyleSpan && it.style == Typeface.ITALIC && spannable.getSpanFlags(it) == Spanned.SPAN_INCLUSIVE_INCLUSIVE
            }
            strikethroughButton.isChecked = spansAtCursor.any {
                it is StrikethroughSpan && spannable.getSpanFlags(it) == Spanned.SPAN_INCLUSIVE_INCLUSIVE
            }
        }

        isUpdatingToggleButtons = false
    }


    private fun setupVoiceNote() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            voiceNoteButton.visibility = View.GONE; return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Toast.makeText(applicationContext, "Dinliyorum...", Toast.LENGTH_SHORT).show()
            }
            override fun onResults(results: Bundle?) {
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let {
                    noteInput.text?.insert(noteInput.selectionStart, it)
                }
            }
            override fun onError(error: Int) { Toast.makeText(applicationContext, "Bir hata oluştu, tekrar deneyin.", Toast.LENGTH_SHORT).show() }
            override fun onBeginningOfSpeech() {}
            override fun onEndOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        voiceNoteButton.setOnClickListener { startSpeechToText() }
    }

    private fun startSpeechToText() {
        when {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> {
                speechRecognizer.startListening(speechRecognizerIntent)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::speechRecognizer.isInitialized) speechRecognizer.destroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processIntent(intent)
    }

    private fun processIntent(intent: Intent) {
        if (intent.hasExtra("NOTE_ID")) {
            currentNoteId = intent.getIntExtra("NOTE_ID", 0)
            deleteButton.visibility = View.VISIBLE
            loadNote()
        } else {
            currentNoteId = null
            deleteButton.visibility = View.GONE
            updateColorSelection(findViewById(R.id.color_default))
            updateWindowBackground()
            noteTitle.text?.clear()
            noteInput.text?.clear()
            if (checklistItems.isNotEmpty()) {
                val oldSize = checklistItems.size
                checklistItems.clear()
                checklistAdapter.notifyItemRangeRemoved(0, oldSize)
            }
        }
        showHistoryButton.visibility = if (currentNoteId != null) View.VISIBLE else View.GONE
    }

    private fun setupChecklist() {
        checklistAdapter = ChecklistItemAdapter(checklistItems)
        checklistRecyclerView.adapter = checklistAdapter
        checklistRecyclerView.layoutManager = LinearLayoutManager(this)
        addChecklistItemButton.setOnClickListener { checklistAdapter.addItem() }
    }

    private fun setupColorPickers() {
        val colorDefault: FrameLayout = findViewById(R.id.color_default)
        val colorYellow: FrameLayout = findViewById(R.id.color_yellow)
        val colorBlue: FrameLayout = findViewById(R.id.color_blue)
        val colorGreen: FrameLayout = findViewById(R.id.color_green)
        val colorPink: FrameLayout = findViewById(R.id.color_pink)
        colorPickers = listOf(colorDefault, colorYellow, colorBlue, colorGreen, colorPink)
        val listeners = mapOf(
            colorDefault to R.color.note_color_default,
            colorYellow to R.color.note_color_yellow,
            colorBlue to R.color.note_color_blue,
            colorGreen to R.color.note_color_green,
            colorPink to R.color.note_color_pink
        )
        listeners.forEach { (view, colorResId) -> view.setOnClickListener { onColorSelected(it, colorResId) } }
    }

    private fun onColorSelected(view: View, colorResId: Int) {
        selectedColor = String.format("#%08X", ContextCompat.getColor(this, colorResId))
        updateColorSelection(view)
        updateWindowBackground()
    }

    private fun updateColorSelection(selectedView: View?) {
        colorPickers.forEach { it.isSelected = (it == selectedView) }
    }

    private fun getContrastingTextColor(backgroundColor: String): Int {
        return try {
            val colorInt = backgroundColor.toColorInt()
            if ((0.299 * Color.red(colorInt) + 0.587 * Color.green(colorInt) + 0.114 * Color.blue(colorInt)) / 255 > 0.5)
                ContextCompat.getColor(this, R.color.black)
            else
                ContextCompat.getColor(this, R.color.white)
        } catch (e: IllegalArgumentException) {
            ContextCompat.getColor(this, R.color.black)
        }
    }

    private fun updateWindowBackground() {
        try {
            window.setBackgroundDrawable(selectedColor.toColorInt().toDrawable())
        } catch (e: IllegalArgumentException) {
            window.setBackgroundDrawable(Color.WHITE.toDrawable())
        }
        val textColor = getContrastingTextColor(selectedColor)
        checklistAdapter.updateColors(textColor, textColor)
        if (checklistAdapter.itemCount > 0) {
            checklistAdapter.notifyItemRangeChanged(0, checklistAdapter.itemCount)
        }
        noteTitle.setTextColor(textColor)
        noteInput.setTextColor(textColor)
    }

    private fun loadNote() {
        lifecycleScope.launch {
            noteDao.getNoteById(currentNoteId ?: return@launch)?.let { note ->
                noteTitle.setText(note.title)
                displayEditHistory(note)
                try {
                    val content = gson.fromJson(note.content, NoteContent::class.java)
                    noteInput.setText(Html.fromHtml(content.text, Html.FROM_HTML_MODE_LEGACY))

                    val oldSize = checklistItems.size
                    checklistItems.clear()
                    checklistAdapter.notifyItemRangeRemoved(0, oldSize)

                    checklistItems.addAll(content.checklist)
                    checklistAdapter.notifyItemRangeInserted(0, checklistItems.size)

                } catch (e: JsonSyntaxException) {
                    noteInput.setText(Html.fromHtml(note.content, Html.FROM_HTML_MODE_LEGACY))

                    val oldSize = checklistItems.size
                    checklistItems.clear()
                    checklistAdapter.notifyItemRangeRemoved(0, oldSize)
                }
                selectedColor = note.color
                updateWindowBackground()
                val colorInt = try { note.color.toColorInt() } catch (e: Exception) { Color.WHITE }
                val viewToSelect = colorPickers.getOrNull(
                    when (colorInt) {
                        ContextCompat.getColor(this@NoteActivity, R.color.note_color_yellow) -> 1
                        ContextCompat.getColor(this@NoteActivity, R.color.note_color_blue) -> 2
                        ContextCompat.getColor(this@NoteActivity, R.color.note_color_green) -> 3
                        ContextCompat.getColor(this@NoteActivity, R.color.note_color_pink) -> 4
                        else -> 0
                    }
                )
                updateColorSelection(viewToSelect)
                updateFormattingButtonsState()
            }
        }
    }

    private fun saveNote() {
        val titleText = noteTitle.text.toString().trim()
        val noteContentText = noteInput.text

        if (titleText.isBlank() && noteContentText.isNullOrBlank() && checklistItems.all { it.text.isBlank() }) {
            Toast.makeText(this, "Not içeriği boş olamaz!", Toast.LENGTH_SHORT).show()
            return
        }
        val noteTextHtml = if(noteContentText.isNullOrBlank()) "" else Html.toHtml(noteContentText, Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)
        val jsonContent = gson.toJson(NoteContent(text = noteTextHtml, checklist = checklistItems))

        lifecycleScope.launch {
            currentNoteId?.let { id ->
                noteDao.getNoteById(id)?.let {
                    val updatedModifications = it.modifiedAt.toMutableList().apply { add(System.currentTimeMillis()) }
                    noteDao.update(it.copy(title = titleText, content = jsonContent, modifiedAt = updatedModifications, color = selectedColor))
                }
            } ?: noteDao.insert(Note(title = titleText, content = jsonContent, createdAt = System.currentTimeMillis(), color = selectedColor))
            updateAllWidgets()
            finish()
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
        appWidgetManager.getAppWidgetIds(componentName).forEach {
            appWidgetManager.notifyAppWidgetViewDataChanged(it, R.id.lv_widget_notes)
        }
    }

    private fun displayEditHistory(note: Note) {
        val historyBuilder = StringBuilder("Oluşturulma: ${formatDate(note.createdAt)}")
        if (note.modifiedAt.isNotEmpty()) {
            historyBuilder.append("\n\nDüzenleme Geçmişi:")
            note.modifiedAt.forEach { timestamp ->
                historyBuilder.append("\n- ${formatDate(timestamp)}")
            }
        }
        editHistoryText.text = historyBuilder.toString()
    }

    private fun formatDate(timestamp: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}