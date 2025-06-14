package com.example.acilnotuygulamasi

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class NoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Tüm widget'ları güncelle
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
            // Widget layout'unu al
            val views = RemoteViews(context.packageName, R.layout.note_widget_layout)

            // Kaydedilmiş notu yükle
            val noteText = NoteStorage.loadNote(context)
            views.setTextViewText(R.id.tv_widget_note, noteText)

            // "Düzenle" butonuna tıklandığında NoteActivity'yi açacak Intent'i oluştur
            val intent = Intent(context, NoteActivity::class.java)
            // Her widget örneği için farklı bir PendingIntent olması için bu flag önemli
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId, // request code olarak widget id'yi kullanmak
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            views.setOnClickPendingIntent(R.id.btn_widget_edit, pendingIntent)

            // Widget'ı güncelle
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}