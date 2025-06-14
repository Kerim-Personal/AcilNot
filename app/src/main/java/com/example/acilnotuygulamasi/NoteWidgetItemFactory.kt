package com.example.acilnotuygulamasi

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.net.toUri // HATA 1 İÇİN EKLENEN IMPORT
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
        val note = notes[position]
        val views = RemoteViews(context.packageName, R.layout.widget_note_item).apply {
            setTextViewText(R.id.tv_widget_item_content, note.content)
        }

        // DÜZENLEME BUTONU İÇİN PENDINGINTENT
        val editIntent = Intent(context, NoteActivity::class.java).apply {
            data = "edit://${note.id}".toUri()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("NOTE_ID", note.id)
        }
        val editPendingIntent = PendingIntent.getActivity(
            context,
            note.id,
            editIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_widget_item_edit, editPendingIntent)


        // SİLME BUTONU İÇİN PENDINGINTENT
        val deleteIntent = Intent(context, NoteDeleteReceiver::class.java).apply {
            action = NoteDeleteReceiver.ACTION_DELETE_NOTE
            putExtra(NoteDeleteReceiver.EXTRA_NOTE_ID, note.id)
        }
        val deletePendingIntent = PendingIntent.getBroadcast(
            context,
            note.id,
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_widget_item_delete, deletePendingIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1

    // HATA 2 İÇİN BU SATIR DÜZELTİLDİ
    override fun getItemId(position: Int): Long = notes[position].id.toLong()

    override fun hasStableIds(): Boolean = true
}