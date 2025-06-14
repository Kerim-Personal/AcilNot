package com.example.acilnotuygulamasi

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.flow.collectLatest
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

        // Adapter'ı başlat
        noteAdapter = NoteAdapter(emptyList()) { note ->
            // Bir nota tıklandığında NoteActivity'yi düzenleme modunda aç
            val intent = Intent(this, NoteActivity::class.java).apply {
                putExtra("NOTE_ID", note.id)
            }
            startActivity(intent)
        }

        recyclerView.adapter = noteAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Yeni not ekle butonu
        fab.setOnClickListener {
            // NoteActivity'yi yeni not modunda aç
            val intent = Intent(this, NoteActivity::class.java)
            startActivity(intent)
        }

        // Veritabanındaki notları dinle ve arayüzü güncelle
        lifecycleScope.launch {
            noteDao.getAllNotes().collectLatest { notes ->
                noteAdapter.updateNotes(notes)
            }
        }
    }
}