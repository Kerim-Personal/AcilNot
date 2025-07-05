package com.codenzi.acilnot

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    enum class SortOrder {
        CREATION_NEWEST, CREATION_OLDEST, CONTENT_AZ
    }

    private lateinit var noteDao: NoteDao
    private lateinit var noteAdapter: NoteAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var toolbar: Toolbar
    private var allNotes: List<Note> = emptyList()
    private var currentSortOrder = SortOrder.CREATION_NEWEST
    private var currentSearchQuery: String? = null
    private var isSelectionMode = false
    private val PREF_THEME_MODE = "theme_selection"

    // YENİ: Sayfayı kaydırmak için kullanılacak bayrak
    private var shouldScrollToTop = false

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        noteDao = NoteDatabase.getDatabase(this).noteDao()
        recyclerView = findViewById(R.id.rv_notes)
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

                    // DEĞİŞTİRİLDİ: Bayrak kontrolü ile güvenilir kaydırma
                    if (shouldScrollToTop) {
                        recyclerView.scrollToPosition(0)
                        shouldScrollToTop = false // Bayrağı sıfırla ki sadece bir kere çalışsın
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
    }

    // DEĞİŞTİRİLDİ: onResume artık doğrudan kaydırma yapmıyor, sadece bayrağı ayarlıyor.
    override fun onResume() {
        super.onResume()
        shouldScrollToTop = true
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
            invalidateOptionsMenu() // Her seçimde menüyü yeniden kontrol et
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
                pinItem.title = "Sabitlemeyi Kaldır"
                pinItem.icon = ContextCompat.getDrawable(this, R.drawable.ic_pin_off)
            } else {
                pinItem.title = "Widget'a Sabitle"
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
        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        val componentName = ComponentName(applicationContext, NoteWidgetProvider::class.java)
        appWidgetManager.getAppWidgetIds(componentName).forEach { appWidgetId ->
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.lv_widget_notes)
        }
    }

    private fun unpinSelectedNotes(notes: List<Note>) {
        if (notes.isEmpty()) return
        val noteIds = notes.map { it.id }
        lifecycleScope.launch {
            noteDao.setPinnedStatus(noteIds, false)
            updateAllWidgets()
            Toast.makeText(applicationContext, "Notların widget sabitlemesi kaldırıldı.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pinNotesToWidget(notes: List<Note>) {
        if (notes.isEmpty()) return
        val noteIds = notes.map { it.id }
        lifecycleScope.launch {
            noteDao.unpinAllNotes() // Önce mevcut tüm pinleri kaldır
            noteDao.setPinnedStatus(noteIds, true)
            updateAllWidgets()
            Toast.makeText(applicationContext, "Seçili notlar widget'a sabitlendi.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareNotes(notes: List<Note>) {
        if (notes.isEmpty()) return
        val shareText = notes.joinToString("\n\n---\n\n") { it.content }
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        startActivity(Intent.createChooser(shareIntent, "Notları Paylaş"))
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
                currentSortOrder = SortOrder.values()[which]
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
}