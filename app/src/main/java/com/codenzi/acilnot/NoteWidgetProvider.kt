package com.codenzi.acilnot

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.net.toUri
import androidx.preference.PreferenceManager // PreferenceManager'ı import etmeyi unutmayın!

class NoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        try {
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        } catch (e: Exception) {
            // Widget güncellenirken genel bir hata olursa logla
            // Burada loglama yapılabilir, ancak kullanıcıya göstermeye gerek yok
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            try {
                val views = RemoteViews(context.packageName, R.layout.note_widget_layout)

            // YENİ: Widget arka planını ayarla
            val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
            val backgroundDrawableName = sharedPrefs.getString("widget_background_selection", "widget_background") // Varsayılan olarak mevcut arka planı kullan
            val backgroundResId = context.resources.getIdentifier(
                backgroundDrawableName, "drawable", context.packageName
            )

            if (backgroundResId != 0) {
                views.setInt(R.id.widget_container_layout, "setBackgroundResource", backgroundResId)
            } else {
                // Eğer kaynak bulunamazsa veya geçersizse varsayılanı kullan
                // DOĞRU KULLANIM BUDUR
                views.setInt(R.id.widget_container_layout, "setBackgroundResource", R.drawable.widget_background)
            }


            val serviceIntent = Intent(context, NoteWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = this.toUri(Intent.URI_INTENT_SCHEME).toUri()
            }
            views.setRemoteAdapter(R.id.lv_widget_notes, serviceIntent)
            views.setEmptyView(R.id.lv_widget_notes, R.id.tv_widget_empty)


            val clickIntent = Intent(context, NoteActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val mutabilityFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }

            val clickPendingIntent = PendingIntent.getActivity(
                context,
                0,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or mutabilityFlag
            )
            views.setPendingIntentTemplate(R.id.lv_widget_notes, clickPendingIntent)


            val newNoteIntent = Intent(context, NoteActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val newNotePendingIntent = PendingIntent.getActivity(
                context,
                1,
                newNoteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_new, newNotePendingIntent)

                appWidgetManager.updateAppWidget(appWidgetId, views)
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.lv_widget_notes)
            } catch (e: Exception) {
                // Widget güncellenirken hata olursa varsayılan bir widget göster
                try {
                    val errorViews = RemoteViews(context.packageName, R.layout.note_widget_layout)
                    errorViews.setTextViewText(R.id.tv_widget_empty, "Widget yüklenirken bir sorun oluştu.\nLütfen daha sonra tekrar deneyin.")
                    appWidgetManager.updateAppWidget(appWidgetId, errorViews)
                } catch (ex: Exception) {
                    // Son çare olarak hiçbir şey yapma
                }
            }
        }
    }
}