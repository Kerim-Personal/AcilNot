// kerim-personal/acilnot/AcilNot-bac63f010f7599eb293834e5058654e744d3d8b5/app/src/main/java/com/codenzi/acilnot/TrashActivity.kt
package com.codenzi.acilnot

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class TrashActivity : AppCompatActivity() {

    private lateinit var noteDao: NoteDao
    private lateinit var deletedNoteAdapter: NoteAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmptyTrash: TextView
    private lateinit var toolbar: Toolbar

    private var isSelectionMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trash)

        toolbar = findViewById(R.id.toolbar_trash)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        noteDao = NoteDatabase.getDatabase(this).noteDao()
        recyclerView = findViewById(R.id.rv_deleted_notes)
        tvEmptyTrash = findViewById(R.id.tv_empty_trash)

        setupRecyclerView()
        observeDeletedNotes()
        setupBackButtonHandler()
    }

    private fun setupRecyclerView() {
        deletedNoteAdapter = NoteAdapter(emptyList(),
            { note -> // Tıklama Olayı
                if (isSelectionMode) {
                    toggleSelection(note)
                } else {
                    showSingleNoteOptionsDialog(note)
                }
            },
            { note -> // Uzun Tıklama Olayı
                if (!isSelectionMode) {
                    enterSelectionMode()
                }
                toggleSelection(note)
            }
        )
        recyclerView.adapter = deletedNoteAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun observeDeletedNotes() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                noteDao.getDeletedNotes().collect { notes ->
                    deletedNoteAdapter.updateNotes(notes)
                    tvEmptyTrash.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
                    recyclerView.visibility = if (notes.isEmpty()) View.GONE else View.VISIBLE
                    if (notes.isEmpty() && isSelectionMode) {
                        exitSelectionMode()
                    }
                }
            }
        }
    }

    private fun enterSelectionMode() {
        isSelectionMode = true
        invalidateOptionsMenu() // Menüyü yeniden çiz
        toolbar.navigationIcon = AppCompatResources.getDrawable(this, R.drawable.ic_close)
        toolbar.setNavigationOnClickListener { exitSelectionMode() }
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        deletedNoteAdapter.clearSelections()
        invalidateOptionsMenu() // Menüyü yeniden çiz
        supportActionBar?.title = getString(R.string.trash_title)
        toolbar.navigationIcon = null
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // Geri okunu tekrar göster
    }

    private fun toggleSelection(note: Note) {
        deletedNoteAdapter.toggleSelection(note.id)
        val count = deletedNoteAdapter.getSelectedItemCount()
        if (count == 0) {
            exitSelectionMode()
        } else {
            supportActionBar?.title = resources.getQuantityString(R.plurals.selection_title, count, count)
            invalidateOptionsMenu()
        }
    }

    private fun showSingleNoteOptionsDialog(note: Note) {
        val options = arrayOf(getString(R.string.restore_note), getString(R.string.delete_permanently))
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.note_options_title))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> restoreNote(note)
                    1 -> showPermanentDeleteConfirmationDialog(listOf(note))
                }
            }
            .show()
    }

    private fun restoreNote(note: Note) {
        lifecycleScope.launch {
            noteDao.restoreNotes(listOf(note.id))
            Toast.makeText(applicationContext, getString(R.string.note_restored_toast), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPermanentDeleteConfirmationDialog(notesToDelete: List<Note>) {
        if (notesToDelete.isEmpty()) return

        val message = if (notesToDelete.size == 1) {
            getString(R.string.dialog_message_delete_permanently)
        } else {
            resources.getQuantityString(R.plurals.delete_notes_confirmation_message, notesToDelete.size, notesToDelete.size)
        }

        AlertDialog.Builder(this)
            .setTitle(resources.getQuantityString(R.plurals.delete_notes_confirmation_title, notesToDelete.size, notesToDelete.size))
            .setMessage(message)
            .setPositiveButton(getString(R.string.dialog_confirm_delete_permanently)) { _, _ ->
                permanentDeleteNotes(notesToDelete)
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    private fun permanentDeleteNotes(notes: List<Note>) {
        lifecycleScope.launch {
            val noteIds = notes.map { it.id }
            noteDao.hardDeleteByIds(noteIds)
            Toast.makeText(applicationContext, resources.getQuantityString(R.plurals.notes_deleted_toast, notes.size, notes.size), Toast.LENGTH_SHORT).show()
            exitSelectionMode()
        }
    }

    private fun setupBackButtonHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isSelectionMode) {
                    exitSelectionMode()
                } else {
                    finish()
                }
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.trash_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_delete_selected).isVisible = isSelectionMode && deletedNoteAdapter.getSelectedItemCount() > 0
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (!isSelectionMode) {
                    finish()
                } else {
                    exitSelectionMode()
                }
                true
            }
            R.id.action_delete_selected -> {
                showPermanentDeleteConfirmationDialog(deletedNoteAdapter.getSelectedNotes())
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}