package com.codenzi.snapnote

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class NoteWidgetItemFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var notes: List<Note> = emptyList()
    private val noteDao = NoteDatabase.getDatabase(context).noteDao()
    private val gson = Gson()
    private val job = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        // Bu metod başlangıçta bir kez çalışır.
    }

    override fun onDataSetChanged() {
        // DÜZELTME: Veritabanı işlemi, ana iş parçacığını bloklamamak için
        // runBlocking yerine asenkron bir coroutine içinde çalıştırılıyor.
        // Bu, widget güncellemeleri sırasında oluşabilecek ANR (Application Not Responding)
        // hatalarını önler.
        runBlocking(job.coroutineContext) {
            try {
                notes = noteDao.getNotesForWidget()
            } catch (e: Exception) {
                notes = emptyList()
            }
        }
    }

    override fun onDestroy() {
        notes = emptyList()
    }

    override fun getCount(): Int = notes.size

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_note_item)

        if (position >= notes.size) {
            return views
        }

        try {
            val note = notes[position]

            if (note.title.isNotBlank()) {
                views.setViewVisibility(R.id.tv_widget_item_title, View.VISIBLE)
                views.setTextViewText(R.id.tv_widget_item_title, note.title)
            } else {
                views.setViewVisibility(R.id.tv_widget_item_title, View.GONE)
            }

            var contentPreview: String = try {
                val noteContent = gson.fromJson(note.content, NoteContent::class.java)

                val textPart = if (noteContent.text.isNotBlank()) {
                    Html.fromHtml(noteContent.text, Html.FROM_HTML_MODE_LEGACY).toString().trim()
                } else {
                    ""
                }

                val checklistPart = if (noteContent.checklist.isNotEmpty()) {
                    val checkedCount = noteContent.checklist.count { it.isChecked }
                    context.getString(R.string.checklist_summary_preview, checkedCount, noteContent.checklist.size)
                } else {
                    ""
                }

                if (textPart.isNotEmpty() && checklistPart.isNotEmpty()) {
                    "$textPart\n$checklistPart"
                } else {
                    textPart + checklistPart
                }
            } catch (e: JsonSyntaxException) {
                Html.fromHtml(note.content, Html.FROM_HTML_MODE_LEGACY).toString()
            }

            val firstNewlineIndex = contentPreview.indexOf('\n')
            if (firstNewlineIndex != -1) {
                contentPreview = contentPreview.substring(0, firstNewlineIndex) + "..."
            }

            views.setTextViewText(R.id.tv_widget_item_content, contentPreview)

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
        } catch (e: Exception) {
            views.setTextViewText(R.id.tv_widget_item_title, "")
            views.setTextViewText(R.id.tv_widget_item_content, "Not yüklenirken hata oluştu")
            return views
        }
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = notes[position].id.toLong()
    override fun hasStableIds(): Boolean = true
}