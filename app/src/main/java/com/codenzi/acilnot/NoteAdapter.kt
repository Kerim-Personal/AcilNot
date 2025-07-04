package com.codenzi.acilnot

import android.graphics.Color
import android.text.Html
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

class NoteAdapter(
    private var notes: List<Note>,
    private val clickListener: (Note) -> Unit,
    private val longClickListener: (Note) -> Unit
) : RecyclerView.Adapter<NoteAdapter.NoteViewHolder>() {

    private val selectedItems = mutableSetOf<Int>()
    private val gson = Gson()

    fun toggleSelection(noteId: Int) {
        val index = notes.indexOfFirst { it.id == noteId }
        if (index == -1) return

        if (selectedItems.contains(noteId)) {
            selectedItems.remove(noteId)
        } else {
            selectedItems.add(noteId)
        }
        // Sadece değişen öğeyi güncelle
        notifyItemChanged(index)
    }

    fun getSelectedNotes(): List<Note> {
        return notes.filter { selectedItems.contains(it.id) }
    }

    fun clearSelections() {
        val previouslySelectedIndices = selectedItems.mapNotNull { selectedId ->
            notes.indexOfFirst { it.id == selectedId }.takeIf { it != -1 }
        }
        selectedItems.clear()
        // Sadece daha önce seçili olan öğeleri güncelle
        previouslySelectedIndices.forEach { notifyItemChanged(it) }
    }

    fun getSelectedItemCount(): Int = selectedItems.size

    inner class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val noteTitle: TextView = itemView.findViewById(R.id.tv_note_title)
        private val noteContent: TextView = itemView.findViewById(R.id.tv_note_content)
        private val cardContainer: MaterialCardView = itemView.findViewById(R.id.note_card_container)

        fun bind(note: Note) {
            // KTX Uzantısı Kullanımı
            noteTitle.isVisible = note.title.isNotBlank()
            noteTitle.text = note.title

            try {
                val content = gson.fromJson(note.content, NoteContent::class.java)
                val textPreview: Spanned = Html.fromHtml(content.text, Html.FROM_HTML_MODE_LEGACY)

                val hasText = textPreview.isNotBlank()
                val hasChecklist = content.checklist.isNotEmpty()

                // İçerik görünürlüğünü en başta ayarla
                noteContent.isVisible = hasText || hasChecklist

                if (hasText) {
                    noteContent.text = textPreview
                } else {
                    noteContent.text = "" // TextView'i temizle
                }

                if (hasChecklist) {
                    val checklistSummary = StringBuilder()
                    if (hasText) {
                        checklistSummary.append("\n\n")
                    }
                    val checkedCount = content.checklist.count { it.isChecked }
                    checklistSummary.append("[Liste: ${checkedCount}/${content.checklist.size} tamamlandı]")
                    noteContent.append(checklistSummary.toString())
                }

            } catch (e: JsonSyntaxException) {
                val preview: Spanned = Html.fromHtml(note.content, Html.FROM_HTML_MODE_LEGACY)
                noteContent.text = preview
                noteContent.isVisible = preview.isNotBlank()
            }

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
        // DiffUtil kullanarak listeyi verimli bir şekilde güncelle
        val diffCallback = NoteDiffCallback(this.notes, newNotes)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        this.notes = newNotes
        diffResult.dispatchUpdatesTo(this)
    }
}

// DiffUtil için karşılaştırıcı sınıf
class NoteDiffCallback(
    private val oldList: List<Note>,
    private val newList: List<Note>
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int = oldList.size
    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        // Öğelerin aynı olup olmadığını ID ile kontrol et
        return oldList[oldItemPosition].id == newList[newItemPosition].id
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        // Öğelerin içeriğinin aynı olup olmadığını kontrol et
        return oldList[oldItemPosition] == newList[newItemPosition]
    }
}