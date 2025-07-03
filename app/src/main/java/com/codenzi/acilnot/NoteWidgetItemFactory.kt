package com.codenzi.acilnot

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Html
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.graphics.toColorInt
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.runBlocking

class NoteWidgetItemFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var notes: List<Note> = emptyList()
    private val noteDao = NoteDatabase.getDatabase(context).noteDao()
    // YENİ: JSON çözümlemesi için Gson nesnesi
    private val gson = Gson()

    override fun onCreate() {
        // Gerekli değil
    }

    override fun onDataSetChanged() {
        runBlocking {
            notes = noteDao.getNotesForWidget()
        }
    }

    override fun onDestroy() {
        notes = emptyList()
    }

    override fun getCount(): Int = notes.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= notes.size) {
            return RemoteViews(context.packageName, R.layout.widget_note_item)
        }

        val note = notes[position]
        val views = RemoteViews(context.packageName, R.layout.widget_note_item)

        // YENİ: Başlığı ayarla
        if (note.title.isNotBlank()) {
            views.setViewVisibility(R.id.tv_widget_item_title, View.VISIBLE)
            views.setTextViewText(R.id.tv_widget_item_title, note.title)
        } else {
            views.setViewVisibility(R.id.tv_widget_item_title, View.GONE)
        }

        // YENİ VE DÜZELTİLMİŞ: İçeriği JSON'dan parse edip okunabilir metin haline getir
        val contentPreview: String = try {
            val noteContent = gson.fromJson(note.content, NoteContent::class.java)

            // Metin kısmını HTML'den arındır
            val textPart = if (noteContent.text.isNotBlank()) {
                Html.fromHtml(noteContent.text, Html.FROM_HTML_MODE_LEGACY).toString().trim()
            } else {
                ""
            }

            // Checklist kısmının özetini oluştur
            val checklistPart = if (noteContent.checklist.isNotEmpty()) {
                val checkedCount = noteContent.checklist.count { it.isChecked }
                "[Liste: ${checkedCount}/${noteContent.checklist.size}]"
            } else {
                ""
            }

            // İki kısmı anlamlı bir şekilde birleştir
            if (textPart.isNotEmpty() && checklistPart.isNotEmpty()) {
                "$textPart\n$checklistPart"
            } else {
                textPart + checklistPart // Sadece biri doluysa veya ikisi de boşsa
            }
        } catch (e: JsonSyntaxException) {
            // JSON parse hatası olursa (eski format notlar için), içeriği doğrudan HTML'den arındır
            Html.fromHtml(note.content, Html.FROM_HTML_MODE_LEGACY).toString()
        }

        views.setTextViewText(R.id.tv_widget_item_content, contentPreview)

        // Mevcut kodun geri kalanı
        try {
            views.setInt(R.id.widget_item_container, "setBackgroundColor", note.color.toColorInt())
        } catch (e: Exception) {
            views.setInt(R.id.widget_item_container, "setBackgroundColor", Color.WHITE)
        }

        val fillInIntent = Intent().apply {
            val extras = Bundle()
            extras.putInt("NOTE_ID", note.id)
            putExtras(extras)
        }

        views.setOnClickFillInIntent(R.id.widget_item_container, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = notes[position].id.toLong()
    override fun hasStableIds(): Boolean = true
}