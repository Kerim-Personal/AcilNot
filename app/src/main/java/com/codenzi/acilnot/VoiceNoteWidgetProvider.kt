package com.codenzi.acilnot

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews

class VoiceNoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val remoteViews = RemoteViews(context.packageName, R.layout.voice_note_widget_idle)

        // --- DEĞİŞİKLİK BURADA ---
        // Servisi doğrudan başlatmak yerine, izin isteyen Activity'i başlatıyoruz.
        val intent = Intent(context, RecordingPermissionActivity::class.java).apply {
            // Activity'nin yeni bir görevde başlamasını sağlar, bu widget'lar için önemlidir.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        // PendingIntent'i Activity için oluşturuyoruz.
        val pendingIntent = PendingIntent.getActivity(context, appWidgetId, intent, flags)

        remoteViews.setOnClickPendingIntent(R.id.btn_start_recording, pendingIntent)
        appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
    }
}