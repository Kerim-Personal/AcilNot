package com.example.acilnotuygulamasi

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.net.toUri
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
            notes = noteDao.getLatest5Notes()
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
            setTextViewText(R.id.tv_widget_item_content, note.content)
        }

        // Sadece bu öğeye özel veriyi (NOTE_ID) içeren "doldurma" niyetini (fill-in intent) oluştur.
        // Bu intent, NoteWidgetProvider'da tanımlanan şablon PendingIntent ile birleşecek.
        val fillInIntent = Intent().apply {
            val extras = Bundle()
            extras.putInt("NOTE_ID", note.id)
            putExtras(extras)
        }

        // Tıklama olayını ana öğeye ata.
        views.setOnClickFillInIntent(R.id.tv_widget_item_content, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = notes[position].id.toLong()
    override fun hasStableIds(): Boolean = true
}