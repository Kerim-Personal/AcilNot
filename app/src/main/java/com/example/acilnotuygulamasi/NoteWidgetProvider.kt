package com.example.acilnotuygulamasi

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.runBlocking

class NoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val noteDao = NoteDatabase.getDatabase(context).noteDao()
            val views = RemoteViews(context.packageName, R.layout.note_widget_layout)

            // runBlocking burada geçici bir çözüm. İdealde widget güncellemeleri
            // WorkManager gibi bir yapıyla yönetilmelidir.
            runBlocking {
                val latestNote = noteDao.getLatestNote()
                if (latestNote != null) {
                    views.setTextViewText(R.id.tv_widget_note, latestNote.content)

                    // Düzenle butonuna tıklandığında NoteActivity'yi doğru not ID'si ile aç
                    val intent = Intent(context, NoteActivity::class.java).apply {
                        putExtra("NOTE_ID", latestNote.id)
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        context,
                        appWidgetId, // Her widget için benzersiz bir request code
                        intent,
                        Pendingent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.btn_widget_edit, pendingIntent)
                } else {
                    views.setTextViewText(R.id.tv_widget_note, "Henüz not yok.")
                    // Not yoksa, yeni not ekleme ekranını aç (ID göndermeden)
                    val intent = Intent(context, NoteActivity::class.java)
                    val pendingIntent = PendingIntent.getActivity(
                        context,
                        appWidgetId, // Her widget için benzersiz bir request code
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.btn_widget_edit, pendingIntent)
                }
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    } // <-- EKSİK OLAN PARANTEZ BURADAYDI
}
