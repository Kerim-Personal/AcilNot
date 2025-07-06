package com.codenzi.acilnot

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews

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

        if (isCurrentlyRecording) {
            remoteViews.setImageViewResource(R.id.btn_record_voice, R.drawable.ic_stop_24)
            remoteViews.setTextViewText(R.id.tv_widget_status, "Durdurmak için dokun")
            remoteViews.setViewVisibility(R.id.tv_widget_timer, View.VISIBLE)
        } else {
            remoteViews.setImageViewResource(R.id.btn_record_voice, R.drawable.ic_microphone_red_24)
            remoteViews.setTextViewText(R.id.tv_widget_status, "Kaydetmek için dokun")
            remoteViews.setViewVisibility(R.id.tv_widget_timer, View.GONE)
            remoteViews.setTextViewText(R.id.tv_widget_timer, "00:00")
        }

        val intent = Intent(context, AudioRecordingService::class.java).apply {
            action = if (isCurrentlyRecording) {
                AudioRecordingService.ACTION_STOP_RECORDING
            } else {
                AudioRecordingService.ACTION_START_RECORDING
            }
        }

        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        remoteViews.setOnClickPendingIntent(R.id.btn_record_voice, pendingIntent)
        appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
    }
}