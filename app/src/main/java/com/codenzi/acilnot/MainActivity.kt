package com.codenzi.acilnot

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

    // Yeni durum yöneticimiz
    private var isSelectionMode = false

    // Tema tercihini kaydetmek için anahtar
    // DÜZELTME: Bu anahtar, preferences.xml dosyasındaki "theme_selection" anahtarıyla eşleşmeli
    private val PREF_THEME_MODE = "theme_selection"

    override fun onCreate(savedInstanceState: Bundle?) {
        // Temayı onCreate'in en başında uygula
        applySavedTheme()
        super.onCreate(savedInstanceState) // super.onCreate() çağrısı temadan sonra yapılmalı

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
                }
            }
        }

        // onBackPressed'i OnBackPressedDispatcher ile değiştirme
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isSelectionMode) {
                    exitSelectionMode()
                } else {
                    isEnabled = false // Callback'i geçici olarak devre dışı bırakın
                    onBackPressedDispatcher.onBackPressed() // Normal geri işlevselliğini çağırın
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }

    private fun applySavedTheme() {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        // DÜZELTME: themeMode'u alırken getString yerine getInt kullanıldı ve
        // AppCompatDelegate.setDefaultNightMode'a String yerine doğrudan int verildi.
        // Ayrıca, varsayılan değer olarak System Varsayılanı doğru şekilde ayarlandı.
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
            // Tıklama dinleyicisi
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
            // Uzun tıklama dinleyicisi
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
        invalidateOptionsMenu() // Menüyü yeniden çizmesi için sistemi tetikle
        toolbar.navigationIcon = AppCompatResources.getDrawable(this, R.drawable.ic_close)
        toolbar.setNavigationOnClickListener { exitSelectionMode() }
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        noteAdapter.clearSelections()
        invalidateOptionsMenu()
        toolbar.title = getString(R.string.app_name) // Başlığı eski haline getir
        toolbar.navigationIcon = null // Kapat ikonunu kaldır
    }

    private fun toggleSelection(note: Note) {
        noteAdapter.toggleSelection(note.id)
        val count = noteAdapter.getSelectedItemCount()
        if (count == 0) {
            exitSelectionMode()
        } else {
            toolbar.title = resources.getQuantityString(R.plurals.selection_title, count, count)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)

        // Arama görünümünü ayarla
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

        // Metod parametrelerindeki '?' (null atanabilirlik) kaldırıldı
        searchItem?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean { // 'item: MenuItem?' yerine 'item: MenuItem'
                // Arama genişlediğinde
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean { // 'item: MenuItem?' yerine 'item: MenuItem'
                // Arama daraldığında, sorguyu temizle
                currentSearchQuery = null
                sortAndFilterList()
                return true
            }
        })
        return super.onCreateOptionsMenu(menu)
    }

    // Menü her gösterileceği zaman bu fonksiyon çağrılır
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_search).isVisible = !isSelectionMode
        menu.findItem(R.id.action_sort).isVisible = !isSelectionMode
        menu.findItem(R.id.action_settings).isVisible = !isSelectionMode
        menu.findItem(R.id.action_share_contextual).isVisible = isSelectionMode
        menu.findItem(R.id.action_delete_contextual).isVisible = isSelectionMode

        // Arama kutusu açıksa ve seçim moduna girilirse, arama kutusunu kapat
        if (isSelectionMode) {
            val searchItem = menu.findItem(R.id.action_search)
            if (searchItem != null && searchItem.isActionViewExpanded) {
                searchItem.collapseActionView()
            }
        }

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val selectedNotes = noteAdapter.getSelectedNotes()
        return when (item.itemId) {
            R.id.action_search -> {
                // Arama View'i ActionViewClass olarak ayarlandığı için burada ek bir işlem yapmaya gerek yok,
                // OnQueryTextListener arama işlemini halledecektir.
                true
            }
            R.id.action_sort -> {
                showSortDialog()
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
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
                    // Notları kalıcı olarak silmek yerine çöp kutusuna taşı
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
}