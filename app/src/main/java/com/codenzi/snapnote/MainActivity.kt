package com.codenzi.snapnote

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.toColorInt
import androidx.core.view.drawToBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class MainActivity : AppCompatActivity() {

    enum class SortOrder {
        CREATION_NEWEST, CREATION_OLDEST, CONTENT_AZ
    }

    private lateinit var noteDao: NoteDao
    private lateinit var noteAdapter: NoteAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var toolbar: Toolbar
    private lateinit var tvEmptyNotes: TextView
    private var allNotes: List<Note> = emptyList()
    private var currentSortOrder = SortOrder.CREATION_NEWEST
    private var currentSearchQuery: String? = null
    private var isSelectionMode = false

    companion object {
        private const val PREF_THEME_MODE = "theme_selection"
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, getString(R.string.mic_permission_granted_widget_available), Toast.LENGTH_SHORT).show()
            updateVoiceWidgets()
        } else {
            Toast.makeText(this, getString(R.string.mic_permission_denied_widget_unavailable), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        noteDao = NoteDatabase.getDatabase(this).noteDao()
        recyclerView = findViewById(R.id.rv_notes)
        tvEmptyNotes = findViewById(R.id.tv_empty_notes)
        val fab: FloatingActionButton = findViewById(R.id.fab_add_note)

        setupRecyclerView()

        fab.setOnClickListener {
            startActivity(Intent(this, NoteActivity::class.java))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                noteDao.getAllNotes().collect { notes ->
                    allNotes = notes
                    sortAndFilterList()

                    if (notes.isEmpty()) {
                        recyclerView.visibility = View.GONE
                        tvEmptyNotes.visibility = View.VISIBLE
                    } else {
                        recyclerView.visibility = View.VISIBLE
                        tvEmptyNotes.visibility = View.GONE
                    }
                }
            }
        }

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isSelectionMode) {
                    exitSelectionMode()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)

        checkAudioPermission()
    }

    private fun applySavedTheme() {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        val themeModeString = sharedPrefs.getString(PREF_THEME_MODE, "system_default")
        val mode = when (themeModeString) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun setupRecyclerView() {
        noteAdapter = NoteAdapter(emptyList(),
            { note ->
                if (isSelectionMode) {
                    toggleSelection(note)
                } else {
                    val intent = Intent(this, NoteActivity::class.java).apply {
                        putExtra("NOTE_ID", note.id)
                    }
                    startActivity(intent)
                }
            },
            { note ->
                if (!isSelectionMode) {
                    enterSelectionMode()
                }
                toggleSelection(note)
            }
        )
        recyclerView.adapter = noteAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun enterSelectionMode() {
        isSelectionMode = true
        invalidateOptionsMenu()
        toolbar.navigationIcon = AppCompatResources.getDrawable(this, R.drawable.ic_close)
        toolbar.setNavigationOnClickListener { exitSelectionMode() }
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        noteAdapter.clearSelections()
        invalidateOptionsMenu()
        toolbar.title = getString(R.string.app_name)
        toolbar.navigationIcon = null
    }

    private fun toggleSelection(note: Note) {
        noteAdapter.toggleSelection(note.id)
        val count = noteAdapter.getSelectedItemCount()
        if (count == 0) {
            exitSelectionMode()
        } else {
            toolbar.title = resources.getQuantityString(R.plurals.selection_title, count, count)
            invalidateOptionsMenu()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView

        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                currentSearchQuery = query
                sortAndFilterList()
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                currentSearchQuery = newText
                sortAndFilterList()
                return false
            }
        })

        searchItem?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean = true
            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                currentSearchQuery = null
                sortAndFilterList()
                return true
            }
        })
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val pinItem = menu.findItem(R.id.action_pin_to_widget)
        menu.findItem(R.id.action_search).isVisible = !isSelectionMode
        menu.findItem(R.id.action_sort).isVisible = !isSelectionMode
        menu.findItem(R.id.action_settings).isVisible = !isSelectionMode
        pinItem.isVisible = isSelectionMode
        menu.findItem(R.id.action_share_contextual).isVisible = isSelectionMode
        menu.findItem(R.id.action_delete_contextual).isVisible = isSelectionMode

        if (isSelectionMode) {
            val selectedNotes = noteAdapter.getSelectedNotes()
            val areAllSelectedPinned = selectedNotes.isNotEmpty() && selectedNotes.all { it.showOnWidget }

            if (areAllSelectedPinned) {
                pinItem.title = getString(R.string.unpin_from_widget)
                pinItem.icon = ContextCompat.getDrawable(this, R.drawable.ic_pin_off)
            } else {
                pinItem.title = getString(R.string.pin_to_widget)
                pinItem.icon = ContextCompat.getDrawable(this, R.drawable.ic_push_pin)
            }

            val searchItem = menu.findItem(R.id.action_search)
            if (searchItem.isActionViewExpanded) {
                searchItem.collapseActionView()
            }
        }

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val selectedNotes = noteAdapter.getSelectedNotes()
        return when (item.itemId) {
            R.id.action_sort -> {
                showSortDialog()
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_pin_to_widget -> {
                val areAllSelectedPinned = selectedNotes.isNotEmpty() && selectedNotes.all { it.showOnWidget }
                if (areAllSelectedPinned) {
                    unpinSelectedNotes(selectedNotes)
                } else {
                    pinNotesToWidget(selectedNotes)
                }
                exitSelectionMode()
                true
            }
            R.id.action_share_contextual -> {
                shareNotes(selectedNotes)
                exitSelectionMode()
                true
            }
            R.id.action_delete_contextual -> {
                deleteNotes(selectedNotes)
                exitSelectionMode()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun updateAllWidgets() {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
            val componentName = ComponentName(applicationContext, NoteWidgetProvider::class.java)
            appWidgetManager.getAppWidgetIds(componentName).forEach { appWidgetId ->
                NoteWidgetProvider.updateAppWidget(applicationContext, appWidgetManager, appWidgetId)
            }
        } catch (e: Exception) {
            Toast.makeText(applicationContext, "An error occurred while updating the widget.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun unpinSelectedNotes(notes: List<Note>) {
        if (notes.isEmpty()) return
        val noteIds = notes.map { it.id }
        lifecycleScope.launch {
            noteDao.setPinnedStatus(noteIds, false)
            updateAllWidgets()
            Toast.makeText(applicationContext, getString(R.string.unpinned_from_widget_toast), Toast.LENGTH_SHORT).show()
        }
    }

    private fun pinNotesToWidget(notes: List<Note>) {
        if (notes.isEmpty()) return
        val noteIds = notes.map { it.id }
        lifecycleScope.launch {
            noteDao.setPinnedStatus(noteIds, true)
            updateAllWidgets()
            Toast.makeText(applicationContext, getString(R.string.notes_pinned_to_widget_toast), Toast.LENGTH_SHORT).show()
        }
    }

    private fun Note.toSharableString(): String {
        val gson = Gson()
        val builder = StringBuilder()
        if (this.title.isNotBlank()) {
            builder.append(this.title).append("\n\n")
        }
        try {
            val noteContent = gson.fromJson(this.content, NoteContent::class.java)
            if (noteContent.text.isNotBlank()) {
                val plainText = Html.fromHtml(noteContent.text, Html.FROM_HTML_MODE_LEGACY).toString().trim()
                builder.append(plainText).append("\n\n")
            }
            if (noteContent.checklist.isNotEmpty()) {
                noteContent.checklist.forEach { item ->
                    val checkbox = if (item.isChecked) "✓" else "☐"
                    builder.append("$checkbox ${item.text}\n")
                }
                builder.append("\n")
            }
        } catch (e: JsonSyntaxException) {
            val plainText = Html.fromHtml(this.content, Html.FROM_HTML_MODE_LEGACY).toString().trim()
            builder.append(plainText)
        }
        return builder.toString().trim()
    }

    private fun shareNotes(notes: List<Note>) {
        if (notes.isEmpty()) return

        if (notes.size == 1) {
            val note = notes.first()
            val noteTitle = note.title.ifBlank { getString(R.string.shared_note_default_title) }

            val noteBitmap = createBitmapFromNote(note)

            if (noteBitmap != null) {
                val noteImageFile = saveBitmapToCache(noteBitmap)
                val urisToShare = ArrayList<Uri>()
                noteImageFile?.let {
                    val imageUri = FileProvider.getUriForFile(this, "$packageName.provider", it)
                    urisToShare.add(imageUri)
                }
                val gson = Gson()
                try {
                    val noteContent = gson.fromJson(note.content, NoteContent::class.java)
                    noteContent.audioFilePath?.let { File(it) }?.let { audioFile ->
                        if (audioFile.exists()) {
                            val audioUri = FileProvider.getUriForFile(this, "$packageName.provider", audioFile)
                            urisToShare.add(audioUri)
                        }
                    }
                } catch (_: Exception) {}

                if (urisToShare.isNotEmpty()) {
                    val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "*/*"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, urisToShare)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, getString(R.string.share_note_chooser_title)))
                }
            } else {
                Toast.makeText(this, getString(R.string.note_too_long_for_image_share_toast), Toast.LENGTH_LONG).show()

                val plainTextBuilder = StringBuilder()
                val htmlTextBuilder = StringBuilder()
                val gson = Gson()
                try {
                    val noteContent = gson.fromJson(note.content, NoteContent::class.java)
                    if (note.title.isNotBlank()) {
                        plainTextBuilder.append(note.title).append("\n\n")
                        htmlTextBuilder.append("<b>").append(note.title).append("</b><br><br>")
                    }
                    if (noteContent.text.isNotBlank()) {
                        plainTextBuilder.append(Html.fromHtml(noteContent.text, Html.FROM_HTML_MODE_LEGACY).toString().trim()).append("\n\n")
                        htmlTextBuilder.append(noteContent.text)
                    }
                } catch(e: Exception) {
                    plainTextBuilder.append(note.toSharableString())
                    htmlTextBuilder.append(note.toSharableString())
                }

                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newHtmlText(noteTitle, plainTextBuilder.toString(), htmlTextBuilder.toString())
                clipboard.setPrimaryClip(clip)

                try {
                    val noteContent = gson.fromJson(note.content, NoteContent::class.java)
                    noteContent.audioFilePath?.let { File(it) }?.let { audioFile ->
                        if (audioFile.exists()) {
                            val audioUri = FileProvider.getUriForFile(this, "$packageName.provider", audioFile)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/*"
                                putExtra(Intent.EXTRA_STREAM, audioUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_audio_chooser_title)))
                        }
                    }
                } catch (_: Exception) {}
            }
        } else {
            val shareText = notes.joinToString("\n\n---\n\n") { it.toSharableString() }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_notes_chooser_title)))
        }
    }

    private fun createBitmapFromNote(note: Note): Bitmap? {
        return try {
            val view = LayoutInflater.from(this).inflate(R.layout.note_render_layout, FrameLayout(this), false)
            val titleView = view.findViewById<TextView>(R.id.render_note_title)
            val contentView = view.findViewById<TextView>(R.id.render_note_content)
            val imageView = view.findViewById<ImageView>(R.id.render_note_image)

            val backgroundColor = try {
                note.color.toColorInt()
            } catch (e: Exception) {
                Color.WHITE
            }
            view.setBackgroundColor(backgroundColor)

            val textColor = getContrastingTextColor(backgroundColor)
            titleView.setTextColor(textColor)
            contentView.setTextColor(textColor)
            contentView.setLinkTextColor(textColor)

            val gson = Gson()
            val noteContent = gson.fromJson(note.content, NoteContent::class.java)

            if (note.title.isNotBlank()) {
                titleView.visibility = View.VISIBLE
                titleView.text = note.title
            } else {
                titleView.visibility = View.GONE
            }

            // --- KESİN ÇÖZÜM: FOTOĞRAFI SENKRON YÜKLEME ---
            if (noteContent.imagePath != null) {
                try {
                    val imageUri = Uri.parse(noteContent.imagePath)
                    val inputStream = contentResolver.openInputStream(imageUri)
                    val imageBitmap = BitmapFactory.decodeStream(inputStream)
                    imageView.setImageBitmap(imageBitmap)
                    imageView.visibility = View.VISIBLE
                    inputStream?.close()
                } catch (e: Exception) {
                    imageView.visibility = View.GONE
                    e.printStackTrace()
                }
            } else {
                imageView.visibility = View.GONE
            }
            // --- DÜZELTME SONU ---

            val contentBuilder = StringBuilder()
            if (noteContent.text.isNotBlank()) {
                contentBuilder.append(noteContent.text)
            }
            if (noteContent.checklist.isNotEmpty()) {
                contentBuilder.append("<br><b>${getString(R.string.checklist_render_title)}</b><br>")
                noteContent.checklist.forEach { item ->
                    val checkbox = if (item.isChecked) "✓" else "☐"
                    val text = Html.escapeHtml(item.text)
                    contentBuilder.append(if (item.isChecked) "$checkbox <s>$text</s><br>" else "$checkbox $text<br>")
                }
            }
            contentView.text = Html.fromHtml(contentBuilder.toString(), Html.FROM_HTML_MODE_COMPACT)

            val displayMetrics = resources.displayMetrics
            val width = (displayMetrics.widthPixels * 0.9).toInt()

            view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )

            if (view.measuredHeight > 8192) {
                return null
            }
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)

            view.drawToBitmap()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getContrastingTextColor(backgroundColor: Int): Int {
        val luma = (0.299 * Color.red(backgroundColor) + 0.587 * Color.green(backgroundColor) + 0.114 * Color.blue(backgroundColor)) / 255
        return if (luma > 0.5) Color.BLACK else Color.WHITE
    }

    private fun saveBitmapToCache(bitmap: Bitmap): File? {
        return try {
            val cachePath = File(cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "note_to_share.png")
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun deleteNotes(notes: List<Note>) {
        if (notes.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle(resources.getQuantityString(R.plurals.move_notes_to_trash_confirmation_title, notes.size, notes.size))
            .setMessage(getString(R.string.move_notes_to_trash_confirmation_message))
            .setPositiveButton(getString(R.string.dialog_move_to_trash)) { _, _ ->
                lifecycleScope.launch {
                    notes.forEach { noteDao.softDeleteById(it.id, System.currentTimeMillis()) }
                    Toast.makeText(applicationContext, resources.getQuantityString(R.plurals.notes_deleted_toast, notes.size, notes.size), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    private fun showSortDialog() {
        val sortOptions = arrayOf(
            getString(R.string.sort_by_creation_date_newest),
            getString(R.string.sort_by_creation_date_oldest),
            getString(R.string.sort_by_content_az)
        )
        val checkedItem = currentSortOrder.ordinal
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.sort_dialog_title))
            .setSingleChoiceItems(sortOptions, checkedItem) { dialog, which ->
                currentSortOrder = SortOrder.entries[which]
                sortAndFilterList()
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun sortAndFilterList() {
        val sortedList = when (currentSortOrder) {
            SortOrder.CREATION_NEWEST -> allNotes.sortedByDescending { it.createdAt }
            SortOrder.CREATION_OLDEST -> allNotes.sortedBy { it.createdAt }
            SortOrder.CONTENT_AZ -> allNotes.sortedBy { it.content.lowercase(Locale.getDefault()) }
        }
        val filteredList = if (currentSearchQuery.isNullOrBlank()) {
            sortedList
        } else {
            val searchQuery = currentSearchQuery!!.lowercase(Locale.getDefault())
            sortedList.filter {
                it.content.lowercase(Locale.getDefault()).contains(searchQuery) ||
                        it.title.lowercase(Locale.getDefault()).contains(searchQuery)
            }
        }
        noteAdapter.updateNotes(filteredList)
    }

    private fun checkAudioPermission() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun updateVoiceWidgets() {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
            val componentName = ComponentName(applicationContext, VoiceMemoWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            val intent = Intent(applicationContext, VoiceMemoWidgetProvider::class.java)
            intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
            applicationContext.sendBroadcast(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}