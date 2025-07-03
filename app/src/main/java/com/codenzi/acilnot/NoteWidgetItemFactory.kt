package com.codenzi.acilnot

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Html
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.graphics.toColorInt
import kotlinx.coroutines.runBlocking

class NoteWidgetItemFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var notes: List<Note> = emptyList()
    private val noteDao = NoteDatabase.getDatabase(context).noteDao()

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
        val views = RemoteViews(context.packageName, R.layout.widget_note_item).apply {
            setTextViewText(R.id.tv_widget_item_content, Html.fromHtml(note.content, Html.FROM_HTML_MODE_LEGACY))

            try {
                setInt(R.id.widget_item_container, "setBackgroundColor", note.color.toColorInt())
            } catch (e: Exception) {
                setInt(R.id.widget_item_container, "setBackgroundColor", Color.WHITE)
            }
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