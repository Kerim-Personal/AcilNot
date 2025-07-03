package com.codenzi.acilnotuygulamasi

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NoteAdapter(
    private var notes: List<Note>,
    private val onItemClick: (Note) -> Unit
) : RecyclerView.Adapter<NoteAdapter.NoteViewHolder>() {

    class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val noteContent: TextView = itemView.findViewById(R.id.tv_note_content)
        val creationDate: TextView = itemView.findViewById(R.id.tv_creation_date)
        val modificationDate: TextView = itemView.findViewById(R.id.tv_modification_date)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.note_item, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val currentNote = notes[position]
        holder.noteContent.text = currentNote.content
        holder.itemView.setOnClickListener {
            onItemClick(currentNote)
        }
        holder.creationDate.text = "Oluşturulma: ${formatDate(currentNote.createdAt)}"

        if (currentNote.modifiedAt.isNotEmpty()) {
            holder.modificationDate.visibility = View.VISIBLE
            holder.modificationDate.text = "Son Düzenleme: ${formatDate(currentNote.modifiedAt.last())}"
        } else {
            holder.modificationDate.visibility = View.GONE
        }

    }

    override fun getItemCount() = notes.size

    fun updateNotes(newNotes: List<Note>) {
        notes = newNotes
        notifyDataSetChanged()
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}