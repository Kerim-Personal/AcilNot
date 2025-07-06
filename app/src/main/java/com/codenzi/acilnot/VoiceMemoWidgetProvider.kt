package com.codenzi.acilnot

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.RemoteViews
import android.Manifest
import androidx.core.content.ContextCompat

class VoiceMemoWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val PREFS_NAME = "voice_memo_widget_prefs"
        private const val PREF_IS_RECORDING = "is_recording"

        fun isRecording(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREF_IS_RECORDING, false)
        }

        fun setRecording(context: Context, recording: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_IS_RECORDING, recording)
                .apply()
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        setRecording(context, false)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(PREF_IS_RECORDING)
            .apply()
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
        val isCurrentlyRecording = isRecording(context)

        // Arayüzü duruma göre güncelle
        if (isCurrentlyRecording) {
            remoteViews.setImageViewResource(R.id.btn_record_voice, R.drawable.ic_stop_24)
            remoteViews.setTextViewText(R.id.tv_widget_status, "Durdurmak için dokun")
        } else {
            remoteViews.setImageViewResource(R.id.btn_record_voice, R.drawable.ic_microphone_24)
            remoteViews.setTextViewText(R.id.tv_widget_status, "Kaydetmek için dokun")
        }

        // Butonun tıklama görevini ayarla: Her zaman RecordingStarterActivity'yi başlatacak.
        val intent = Intent(context, RecordingStarterActivity::class.java)
        // Her tıklamanın yeni bir olay olmasını sağlamak için FLAG_ACTIVITY_NEW_TASK ekliyoruz.
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        val pendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId, // Benzersiz bir istek kodu
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        remoteViews.setOnClickPendingIntent(R.id.btn_record_voice, pendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
    }
}