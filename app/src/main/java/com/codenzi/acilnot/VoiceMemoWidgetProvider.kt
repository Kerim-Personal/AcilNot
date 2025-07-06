package com.codenzi.acilnot

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.RemoteViews
import android.content.ComponentName
import android.Manifest
import androidx.core.content.ContextCompat
import android.widget.Toast

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
        try {
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_voice_memo)
            
            // Check if we have permission
            val hasPermission = ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            
            if (!hasPermission) {
                // Show permission required state
                remoteViews.setImageViewResource(R.id.btn_record_voice, R.drawable.ic_microphone_24)
                remoteViews.setTextViewText(R.id.tv_widget_status, "İzin gerekli")
                
                // Create intent to open main activity for permission
                val intent = Intent(context, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                val pendingIntent = PendingIntent.getActivity(
                    context, 
                    appWidgetId, 
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                remoteViews.setOnClickPendingIntent(R.id.btn_record_voice, pendingIntent)
            } else {
                // Permission granted, handle recording
                val isCurrentlyRecording = isRecording(context)
                
                val intent = Intent(context, AudioRecordingService::class.java).apply {
                    action = if (isCurrentlyRecording) {
                        AudioRecordingService.ACTION_STOP_RECORDING
                    } else {
                        AudioRecordingService.ACTION_START_RECORDING
                    }
                    putExtra("widget_id", appWidgetId)
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

                // Update UI based on recording state
                if (isCurrentlyRecording) {
                    remoteViews.setImageViewResource(R.id.btn_record_voice, R.drawable.ic_stop_24)
                    remoteViews.setTextViewText(R.id.tv_widget_status, "Durdurmak için dokun")
                } else {
                    remoteViews.setImageViewResource(R.id.btn_record_voice, R.drawable.ic_microphone_24)
                    remoteViews.setTextViewText(R.id.tv_widget_status, "Kaydetmek için dokun")
                }
                
                remoteViews.setOnClickPendingIntent(R.id.btn_record_voice, pendingIntent)
            }
            
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        } catch (e: Exception) {
            e.printStackTrace()
            // Create a fallback view in case of errors
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_voice_memo)
            remoteViews.setImageViewResource(R.id.btn_record_voice, R.drawable.ic_microphone_24)
            remoteViews.setTextViewText(R.id.tv_widget_status, "Hata oluştu")
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        try {
            when (intent.action) {
                AudioRecordingService.ACTION_UPDATE_WIDGET -> {
                    val isRecording = intent.getBooleanExtra("is_recording", false)
                    setRecording(context, isRecording)
                    
                    // Update all widgets
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val componentName = ComponentName(context, VoiceMemoWidgetProvider::class.java)
                    val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                    onUpdate(context, appWidgetManager, appWidgetIds)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}