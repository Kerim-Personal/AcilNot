package com.codenzi.acilnot

import android.graphics.Color // Import eklendi
import android.graphics.PorterDuff // Import eklendi
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import androidx.recyclerview.widget.RecyclerView

class ChecklistItemAdapter(
    private val items: MutableList<ChecklistItem>
) : RecyclerView.Adapter<ChecklistItemAdapter.ChecklistItemViewHolder>() {

    private var currentTextColor: Int = Color.BLACK // Varsayılan renkler eklendi
    private var currentIconTint: Int = Color.BLACK // Varsayılan renkler eklendi

    // Renkleri güncellemek için yeni metot
    fun updateColors(textColor: Int, iconTint: Int) {
        this.currentTextColor = textColor
        this.currentIconTint = iconTint
        // notifyDataSetChanged() burada çağrılmaz, NoteActivity'den çağrılır.
    }

    inner class ChecklistItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val checkBox: CheckBox = itemView.findViewById(R.id.cb_item)
        val editText: EditText = itemView.findViewById(R.id.et_item_text)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.btn_delete_item)

        init {
            deleteButton.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    items.removeAt(position)
                    notifyItemRemoved(position)
                }
            }

            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        items[position].text = s.toString()
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            })

            checkBox.setOnCheckedChangeListener { _, isChecked ->
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    items[position].isChecked = isChecked
                }
            }
        }

        // ChecklistItemAdapter'dan alınan renkleri bağla
        fun bindColors() {
            editText.setTextColor(currentTextColor)
            // CheckBox metin rengini ayarla (eğer CheckBox'ta metin varsa)
            checkBox.setTextColor(currentTextColor)
            // Silme butonu ikon rengini ayarla
            deleteButton.setColorFilter(currentIconTint, PorterDuff.Mode.SRC_IN)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChecklistItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.checklist_item, parent, false)
        return ChecklistItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChecklistItemViewHolder, position: Int) {
        val item = items[position]
        holder.editText.setText(item.text)
        holder.checkBox.isChecked = item.isChecked
        holder.bindColors() // Renkleri bağlamak için yeni metodu çağır
    }

    override fun getItemCount(): Int = items.size

    fun addItem() {
        items.add(ChecklistItem("", false))
        notifyItemInserted(items.size - 1)
    }
}