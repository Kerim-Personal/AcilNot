package com.codenzi.acilnot

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import android.content.ComponentName

class VoiceMemoWidgetProvider : AppWidgetProvider() {

    companion object {
        var isRecording = false // Basit bir durum takibi için
    }

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
        val remoteViews = RemoteViews(context.packageName, R.layout.widget_voice_memo)
        val intent = Intent(context, AudioRecordingService::class.java).apply {
            // Duruma göre servise doğru eylemi gönder
            action = if (isRecording) {
                AudioRecordingService.ACTION_STOP_RECORDING
            } else {
                AudioRecordingService.ACTION_START_RECORDING
            }
        }

        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                context, appWidgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                context, appWidgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        remoteViews.setOnClickPendingIntent(R.id.btn_record_voice, pendingIntent)
        appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Servisten gelen UI güncelleme isteklerini dinle ve durumu değiştir
        if (intent.action == AudioRecordingService.ACTION_START_RECORDING) {
            isRecording = true
        } else if (intent.action == AudioRecordingService.ACTION_STOP_RECORDING) {
            isRecording = false
        }
        // Widget'ları yeni duruma göre güncellemek için onUpdate'i tetikle
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, VoiceMemoWidgetProvider::class.java)
        onUpdate(context, appWidgetManager, appWidgetManager.getAppWidgetIds(componentName))

        super.onReceive(context, intent)
    }
}