package com.codenzi.acilnot

import android.graphics.Color
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NoteAdapter(
    private var notes: List<Note>,
    private val clickListener: (Note) -> Unit,
    private val longClickListener: (Note) -> Unit
) : RecyclerView.Adapter<NoteAdapter.NoteViewHolder>() {

    private val selectedItems = mutableSetOf<Int>()
    private val gson = Gson()

    fun toggleSelection(noteId: Int) {
        if (selectedItems.contains(noteId)) {
            selectedItems.remove(noteId)
        } else {
            selectedItems.add(noteId)
        }
        notifyDataSetChanged()
    }

    fun getSelectedNotes(): List<Note> {
        return notes.filter { selectedItems.contains(it.id) }
    }

    fun clearSelections() {
        selectedItems.clear()
        notifyDataSetChanged()
    }

    fun getSelectedItemCount(): Int = selectedItems.size

    inner class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // YENİ: Başlık TextView referansı
        private val noteTitle: TextView = itemView.findViewById(R.id.tv_note_title)
        private val noteContent: TextView = itemView.findViewById(R.id.tv_note_content)
        // DÜZELTME: Tarih TextView'leri kaldırıldı
        // private val creationDate: TextView = itemView.findViewById(R.id.tv_creation_date)
        // private val modificationDate: TextView = itemView.findViewById(R.id.tv_modification_date)
        private val cardContainer: MaterialCardView = itemView.findViewById(R.id.note_card_container)

        fun bind(note: Note) {
            // YENİ: Başlığı ayarla veya gizle
            if (note.title.isNotBlank()) {
                noteTitle.visibility = View.VISIBLE
                noteTitle.text = note.title
            } else {
                noteTitle.visibility = View.GONE
            }

            try {
                val content = gson.fromJson(note.content, NoteContent::class.java)
                val previewBuilder = StringBuilder()

                if (content.text.isNotBlank()) {
                    previewBuilder.append(Html.fromHtml(content.text, Html.FROM_HTML_MODE_LEGACY).toString().trim())
                }

                if (content.checklist.isNotEmpty()) {
                    if (previewBuilder.isNotEmpty()) {
                        previewBuilder.append("\n\n")
                    }
                    val checkedCount = content.checklist.count { it.isChecked }
                    previewBuilder.append("[Liste: ${checkedCount}/${content.checklist.size} tamamlandı]")
                }

                noteContent.text = previewBuilder.toString()
            } catch (e: JsonSyntaxException) {
                noteContent.text = Html.fromHtml(note.content, Html.FROM_HTML_MODE_LEGACY)
            }

            // DÜZELTME: Oluşturulma ve düzenleme tarihleri artık burada gösterilmiyor
            // creationDate.text = "Oluşturulma: ${formatDate(note.createdAt)}"
            // modificationDate.visibility = if (note.modifiedAt.isNotEmpty()) {
            //     modificationDate.text = "Son Düzenleme: ${formatDate(note.modifiedAt.last())}"
            //     View.VISIBLE
            // } else {
            //     View.GONE
            // }

            if (selectedItems.contains(note.id)) {
                cardContainer.strokeWidth = 8
                cardContainer.strokeColor = ContextCompat.getColor(cardContainer.context, android.R.color.holo_blue_dark)
            } else {
                cardContainer.strokeWidth = 0
            }

            try {
                cardContainer.setCardBackgroundColor(note.color.toColorInt())
            } catch (e: Exception) {
                cardContainer.setCardBackgroundColor(Color.WHITE)
            }

            itemView.setOnClickListener { clickListener(note) }
            itemView.setOnLongClickListener {
                longClickListener(note)
                true
            }
        }

        private fun formatDate(timestamp: Long): String {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.note_item, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(notes[position])
    }

    override fun getItemCount() = notes.size

    fun updateNotes(newNotes: List<Note>) {
        notes = newNotes
        notifyDataSetChanged()
    }
}