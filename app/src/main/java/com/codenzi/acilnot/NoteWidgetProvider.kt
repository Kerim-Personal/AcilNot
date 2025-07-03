package com.codenzi.acilnot

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.net.toUri

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
            val views = RemoteViews(context.packageName, R.layout.note_widget_layout)

            // ListView'i dolduracak olan Service Intent'i
            val serviceIntent = Intent(context, NoteWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = this.toUri(Intent.URI_INTENT_SCHEME).toUri()
            }
            views.setRemoteAdapter(R.id.lv_widget_notes, serviceIntent)
            views.setEmptyView(R.id.lv_widget_notes, R.id.tv_widget_empty)

            // --- YENİ VE DOĞRU YÖNTEM ---
            // ListView öğelerine tıklandığında NoteActivity'yi açacak olan TIKLAMA ŞABLONUNU oluştur.
            val clickIntent = Intent(context, NoteActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            // PendingIntent.FLAG_MUTABLE, içine fillInIntent'in eklenebilmesi için gereklidir.
            val clickPendingIntent = PendingIntent.getActivity(
                context,
                0, // Bu requestCode şablon için sabit kalabilir.
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.lv_widget_notes, clickPendingIntent)
            // -----------------------------


            // "Yeni Not Ekle" butonu için intent
            val newNoteIntent = Intent(context, NoteActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val newNotePendingIntent = PendingIntent.getActivity(
                context,
                1, // Farklı bir requestCode kullanmak çakışmaları önler.
                newNoteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_new, newNotePendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            // Liste verilerinin güncellendiğini sisteme bildir.
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.lv_widget_notes)
        }
    }
}