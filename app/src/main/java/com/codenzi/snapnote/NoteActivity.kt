package com.codenzi.snapnote

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
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
import coil.load
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.io.IOException
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

    private lateinit var audioPlayerContainer: View
    private lateinit var playPauseButton: ImageButton
    private lateinit var audioTitleText: TextView
    private var mediaPlayer: MediaPlayer? = null
    private var audioPath: String? = null

    private lateinit var ivImagePreview: ImageView
    private var imagePath: String? = null

    private var isListening = false
    private val recognizedTextBuilder = StringBuilder()
    private var utteranceStartPosition = 0

    // YENİ: Notun widget'tan gelip gelmediğini tutacak bir değişken ekliyoruz.
    private var isFromWidget = false

    private val restartHandler = Handler(Looper.getMainLooper())

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                toggleSpeechToText()
            } else {
                Toast.makeText(this, getString(R.string.microphone_permission_needed), Toast.LENGTH_SHORT).show()
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

        audioPlayerContainer = findViewById(R.id.ll_audio_player)
        playPauseButton = findViewById(R.id.btn_play_pause)
        audioTitleText = findViewById(R.id.tv_audio_title)

        ivImagePreview = findViewById(R.id.iv_image_preview)

        ivImagePreview.setOnClickListener {
            imagePath?.let { path ->
                val intent = Intent(this, PhotoViewActivity::class.java).apply {
                    putExtra("IMAGE_URI", path)
                }
                startActivity(intent)
            }
        }

        setupListeners()
        setupChecklist()
        setupColorPickers()
        setupVoiceNote()

        processIntent(intent)

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                performSave()
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }

    override fun onStop() {
        super.onStop()
        releaseMediaPlayer()
        if (isListening) {
            stopListening()
        }
        performSave()
    }

    private fun setupListeners() {
        showHistoryButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.edit_history_dialog_title))
                .setMessage(editHistoryText.text)
                .setPositiveButton(getString(R.string.dialog_ok), null)
                .show()
        }

        boldButton.setOnClickListener { toggleStyle(Typeface.BOLD) }
        italicButton.setOnClickListener { toggleStyle(Typeface.ITALIC) }
        strikethroughButton.setOnClickListener { toggleStyle(-1) }

        noteInput.setOnSelectionChangedListener { _, _ ->
            updateFormattingButtonsState()
        }

        saveButton.setOnClickListener {
            val titleText = noteTitle.text.toString().trim()
            val noteContentText = noteInput.text
            if (titleText.isBlank() && noteContentText.isNullOrBlank() && checklistItems.all { it.text.isBlank() } && imagePath == null) {
                Toast.makeText(this, R.string.toast_empty_note, Toast.LENGTH_SHORT).show()
            } else {
                performSave()
                finish()
            }
        }
        deleteButton.setOnClickListener { showDeleteConfirmationDialog() }
        playPauseButton.setOnClickListener { togglePlayback() }
    }

    private fun performSave() {
        val titleText = noteTitle.text.toString().trim()
        val noteContentText = noteInput.text

        if (titleText.isBlank() && noteContentText.isNullOrBlank() && checklistItems.all { it.text.isBlank() } && imagePath == null) {
            return
        }

        setResult(Activity.RESULT_OK)

        val noteTextHtml = if (noteContentText.isNullOrBlank()) "" else Html.toHtml(noteContentText, Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)
        val jsonContent = gson.toJson(NoteContent(text = noteTextHtml, checklist = checklistItems, audioFilePath = audioPath, imagePath = imagePath))

        lifecycleScope.launch {
            if (currentNoteId != null) {
                noteDao.getNoteById(currentNoteId!!)?.let {
                    val updatedModifications = it.modifiedAt.toMutableList().apply { add(System.currentTimeMillis()) }
                    val updatedNote = it.copy(
                        title = titleText,
                        content = jsonContent,
                        modifiedAt = updatedModifications,
                        color = selectedColor
                    )
                    noteDao.update(updatedNote)
                }
            } else {
                val newNote = Note(
                    title = titleText,
                    content = jsonContent,
                    createdAt = System.currentTimeMillis(),
                    color = selectedColor,
                    // YENİ: Eğer not widget'tan oluşturulduysa, `showOnWidget` değerini true yap.
                    showOnWidget = isFromWidget
                )
                val newId = noteDao.insert(newNote)
                currentNoteId = newId.toInt()
            }
            updateAllWidgets()
        }
    }

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

    private fun updateFormattingButtonsState() {
        isUpdatingToggleButtons = true

        val spannable = noteInput.text ?: return
        val position = noteInput.selectionStart
        val selectionEnd = noteInput.selectionEnd

        if (position != selectionEnd) {
            val boldSpans = spannable.getSpans(position, selectionEnd, StyleSpan::class.java)
            boldButton.isChecked = boldSpans.any { it.style == Typeface.BOLD }

            val italicSpans = spannable.getSpans(position, selectionEnd, StyleSpan::class.java)
            italicButton.isChecked = italicSpans.any { it.style == Typeface.ITALIC }

            val strikeSpans = spannable.getSpans(position, selectionEnd, StrikethroughSpan::class.java)
            strikethroughButton.isChecked = strikeSpans.isNotEmpty()
        } else {
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

    private fun restartListeningWithDelay() {
        restartHandler.postDelayed({
            if (isListening) {
                try {
                    speechRecognizer.startListening(speechRecognizerIntent)
                } catch (e: Exception) {
                    stopListening()
                }
            }
        }, 100)
    }

    private fun setupVoiceNote() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            voiceNoteButton.visibility = View.GONE
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() { utteranceStartPosition = recognizedTextBuilder.length }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                val partialText = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                if (partialText.isNotBlank()) {
                    recognizedTextBuilder.setLength(utteranceStartPosition)
                    recognizedTextBuilder.append(partialText)
                    noteInput.setText(recognizedTextBuilder.toString())
                    noteInput.setSelection(noteInput.length())
                }
            }

            override fun onResults(results: Bundle?) {
                val finalText = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                recognizedTextBuilder.setLength(utteranceStartPosition)
                recognizedTextBuilder.append(finalText)
                if (finalText.isNotBlank()) {
                    recognizedTextBuilder.append(" ")
                }
                noteInput.setText(recognizedTextBuilder.toString())
                noteInput.setSelection(noteInput.length())
            }

            override fun onEndOfSpeech() {
                if (isListening) {
                    restartListeningWithDelay()
                }
            }

            override fun onError(error: Int) {
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS || error == SpeechRecognizer.ERROR_AUDIO) {
                    stopListening()
                    Toast.makeText(applicationContext, getString(R.string.critical_error_recording_stopped), Toast.LENGTH_SHORT).show()
                } else if (isListening) {
                    restartListeningWithDelay()
                }
            }
        })

        voiceNoteButton.setOnClickListener {
            toggleSpeechToText()
        }
    }

    private fun toggleSpeechToText() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (!isListening) {
            startListening()
        } else {
            stopListening()
        }
    }

    private fun startListening() {
        isListening = true
        voiceNoteButton.setImageResource(R.drawable.ic_microphone_red_24)
        Toast.makeText(applicationContext, getString(R.string.speech_listening), Toast.LENGTH_SHORT).show()
        recognizedTextBuilder.clear()
        val currentText = noteInput.text.toString()
        recognizedTextBuilder.append(currentText)
        if (currentText.isNotEmpty() && !currentText.endsWith(" ")) {
            recognizedTextBuilder.append(" ")
        }
        speechRecognizer.startListening(speechRecognizerIntent)
    }

    private fun stopListening() {
        if (!isListening) return
        isListening = false
        restartHandler.removeCallbacksAndMessages(null)
        speechRecognizer.stopListening()
        voiceNoteButton.setImageResource(R.drawable.ic_microphone_24)
    }

    override fun onDestroy() {
        super.onDestroy()
        restartHandler.removeCallbacksAndMessages(null)
        speechRecognizer.destroy()
        releaseMediaPlayer()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processIntent(intent)
    }

    private fun processIntent(intent: Intent) {
        // YENİ: Intent'ten gelen "FROM_WIDGET" extrasını kontrol ediyoruz.
        isFromWidget = intent.getBooleanExtra("FROM_WIDGET", false)

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
            ivImagePreview.visibility = View.GONE
            imagePath = null
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
        val colorPurple: FrameLayout = findViewById(R.id.color_purple)
        val colorOrange: FrameLayout = findViewById(R.id.color_orange)

        colorPickers = listOf(colorDefault, colorYellow, colorBlue, colorGreen, colorPink, colorPurple, colorOrange)

        val listeners = mapOf(
            colorDefault to R.color.note_color_default,
            colorYellow to R.color.note_color_yellow,
            colorBlue to R.color.note_color_blue,
            colorGreen to R.color.note_color_green,
            colorPink to R.color.note_color_pink,
            colorPurple to R.color.note_color_purple,
            colorOrange to R.color.note_color_orange
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

                    if (content.audioFilePath != null) {
                        audioPath = content.audioFilePath
                        audioPlayerContainer.visibility = View.VISIBLE
                        audioTitleText.text = note.title.ifBlank { getString(R.string.voice_recording_title) }
                        prepareMediaPlayer()
                    } else {
                        audioPlayerContainer.visibility = View.GONE
                        audioPath = null
                    }

                    if (content.imagePath != null) {
                        imagePath = content.imagePath
                        ivImagePreview.visibility = View.VISIBLE
                        ivImagePreview.load(content.imagePath) {
                            crossfade(true)
                            placeholder(R.drawable.ic_image_24)
                            error(R.drawable.ic_image_24)
                        }
                    } else {
                        imagePath = null
                        ivImagePreview.visibility = View.GONE
                    }

                } catch (e: JsonSyntaxException) {
                    noteInput.setText(Html.fromHtml(note.content, Html.FROM_HTML_MODE_LEGACY))
                    val oldSize = checklistItems.size
                    checklistItems.clear()
                    checklistAdapter.notifyItemRangeRemoved(0, oldSize)
                    audioPlayerContainer.visibility = View.GONE
                    audioPath = null
                    ivImagePreview.visibility = View.GONE
                    imagePath = null
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
                        ContextCompat.getColor(this@NoteActivity, R.color.note_color_purple) -> 5
                        ContextCompat.getColor(this@NoteActivity, R.color.note_color_orange) -> 6
                        else -> 0
                    }
                )
                updateColorSelection(viewToSelect)
                updateFormattingButtonsState()
            }
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
                Toast.makeText(applicationContext, R.string.note_moved_to_trash_toast, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun updateAllWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, NoteWidgetProvider::class.java)
        appWidgetManager.getAppWidgetIds(componentName).forEach { appWidgetId ->
            NoteWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId)
        }
    }

    private fun displayEditHistory(note: Note) {
        val historyBuilder = StringBuilder("${getString(R.string.creation_date_label, formatDate(note.createdAt))}")
        if (note.modifiedAt.isNotEmpty()) {
            historyBuilder.append("\n\n${getString(R.string.edit_history_title)}")
            note.modifiedAt.forEach { timestamp ->
                historyBuilder.append("\n- ${formatDate(timestamp)}")
            }
        }
        editHistoryText.text = historyBuilder.toString()
    }

    private fun formatDate(timestamp: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

    private fun prepareMediaPlayer() {
        releaseMediaPlayer()
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(audioPath)
                prepareAsync()
                setOnPreparedListener {
                    playPauseButton.isEnabled = true
                }
                setOnCompletionListener {
                    playPauseButton.setImageResource(android.R.drawable.ic_media_play)
                    playPauseButton.contentDescription = getString(R.string.play)
                }
                playPauseButton.setImageResource(android.R.drawable.ic_media_play)
                playPauseButton.contentDescription = getString(R.string.play)
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(this@NoteActivity, getString(R.string.audio_file_cannot_be_played), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun togglePlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                playPauseButton.setImageResource(android.R.drawable.ic_media_play)
                playPauseButton.contentDescription = getString(R.string.play)
            } else {
                it.start()
                playPauseButton.setImageResource(android.R.drawable.ic_media_pause)
                playPauseButton.contentDescription = getString(R.string.pause)
            }
        }
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}