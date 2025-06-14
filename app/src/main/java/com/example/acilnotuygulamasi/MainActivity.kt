package com.example.acilnotuygulamasi

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var noteDao: NoteDao
    private lateinit var noteAdapter: NoteAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        noteDao = NoteDatabase.getDatabase(this).noteDao()

        val recyclerView: RecyclerView = findViewById(R.id.rv_notes)
        val fab: FloatingActionButton = findViewById(R.id.fab_add_note)

        // Adapter'ı basit tıklama dinleyicisi ile başlat
        noteAdapter = NoteAdapter(emptyList()) { note ->
            // Tıklanan notu düzenlemek için NoteActivity'yi aç
            val intent = Intent(this, NoteActivity::class.java).apply {
                putExtra("NOTE_ID", note.id)
            }
            startActivity(intent)
        }

        recyclerView.adapter = noteAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        fab.setOnClickListener {
            startActivity(Intent(this, NoteActivity::class.java))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                noteDao.getAllNotes().collect { notes ->
                    noteAdapter.updateNotes(notes)
                }
            }
        }
    }
}