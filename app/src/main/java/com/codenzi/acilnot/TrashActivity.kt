package com.codenzi.acilnot

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class TrashActivity : AppCompatActivity() {

    private lateinit var noteDao: NoteDao
    private lateinit var deletedNoteAdapter: NoteAdapter // NotAdapter'ı burada da kullanacağız
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmptyTrash: TextView
    private lateinit var toolbar: Toolbar

    private var deletedNotes: List<Note> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trash)

        toolbar = findViewById(R.id.toolbar_trash)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // Geri butonu ekle

        noteDao = NoteDatabase.getDatabase(this).noteDao()
        recyclerView = findViewById(R.id.rv_deleted_notes)
        tvEmptyTrash = findViewById(R.id.tv_empty_trash)

        setupRecyclerView()
        observeDeletedNotes()
    }

    private fun setupRecyclerView() {
        deletedNoteAdapter = NoteAdapter(emptyList(),
            // Tıklama dinleyicisi (çöp kutusundaki notlar için farklı işlem)
            { note ->
                showTrashNoteOptionsDialog(note)
            },
            // Uzun tıklama dinleyicisi (şimdilik aynı, sonra değiştirilebilir)
            { note ->
                // Uzun tıklama için seçim modu eklenebilir, şimdilik tıklama ile aynı
                showTrashNoteOptionsDialog(note)
            }
        )
        recyclerView.adapter = deletedNoteAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun observeDeletedNotes() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                noteDao.getDeletedNotes().collect { notes ->
                    deletedNotes = notes
                    deletedNoteAdapter.updateNotes(deletedNotes)
                    if (notes.isEmpty()) {
                        tvEmptyTrash.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                    } else {
                        tvEmptyTrash.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun showTrashNoteOptionsDialog(note: Note) {
        val options = arrayOf("Notu Geri Yükle", "Kalıcı Olarak Sil")
        AlertDialog.Builder(this)
            .setTitle("Not Seçenekleri")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> restoreNote(note)
                    1 -> showPermanentDeleteConfirmationDialog(note)
                }
            }
            .show()
    }

    private fun restoreNote(note: Note) {
        lifecycleScope.launch {
            noteDao.restoreNotes(listOf(note.id))
            Toast.makeText(applicationContext, "Not geri yüklendi.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPermanentDeleteConfirmationDialog(note: Note) {
        AlertDialog.Builder(this)
            .setTitle("Notu Kalıcı Olarak Sil")
            .setMessage("Bu notu kalıcı olarak silmek istediğinizden emin misiniz? Bu işlem geri alınamaz.")
            .setPositiveButton("Evet, Kalıcı Olarak Sil") { _, _ -> permanentDeleteNote(note) }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun permanentDeleteNote(note: Note) {
        lifecycleScope.launch {
            noteDao.hardDeleteById(note.id)
            Toast.makeText(applicationContext, "Not kalıcı olarak silindi.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish() // Geri butonuna basıldığında aktiviteyi kapat
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}