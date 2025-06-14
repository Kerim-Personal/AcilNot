package com.example.acilnotuygulamasi

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.net.toUri
import kotlinx.coroutines.runBlocking

class NoteWidgetItemFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var notes: List<Note> = emptyList()
    private val noteDao = NoteDatabase.getDatabase(context).noteDao()

    override fun onCreate() {}

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

        // Tıklama olayını DOĞRUDAN NoteActivity'yi (düzenleme modu) açacak şekilde ayarla
        val editIntent = Intent(context, NoteActivity::class.java).apply {
            putExtra("NOTE_ID", note.id)
            // Her intent'in benzersiz olması için data ekliyoruz
            data = "acilnot://edit/${note.id}".toUri()
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            note.id,
            editIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.tv_widget_item_content, pendingIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = notes[position].id.toLong()
    override fun hasStableIds(): Boolean = true
}