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
import android.util.Log

class VoiceMemoWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "VoiceMemoWidget"
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
        Log.d(TAG, "onUpdate called with ${appWidgetIds.size} widgets")
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        Log.d(TAG, "Updating widget $appWidgetId")
        
        try {
            // Create simple RemoteViews first
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_voice_memo)
            
            // Check if we have permission
            val hasPermission = ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            
            Log.d(TAG, "Has permission: $hasPermission")
            
            if (!hasPermission) {
                // Permission not granted - show permission required
                remoteViews.setImageViewResource(R.id.btn_record_voice, R.drawable.ic_microphone_24)
                remoteViews.setTextViewText(R.id.tv_widget_status, "İzin gerekli")
                
                // Simple intent to main activity
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                
                val pendingIntent = PendingIntent.getActivity(
                    context, 
                    appWidgetId, 
                    intent,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }
                )
                
                remoteViews.setOnClickPendingIntent(R.id.btn_record_voice, pendingIntent)
                Log.d(TAG, "Set permission required state")
                
            } else {
                // Permission granted - handle recording
                val isCurrentlyRecording = isRecording(context)
                Log.d(TAG, "Currently recording: $isCurrentlyRecording")
                
                // Update UI based on state
                if (isCurrentlyRecording) {
                    remoteViews.setImageViewResource(R.id.btn_record_voice, R.drawable.ic_stop_24)
                    remoteViews.setTextViewText(R.id.tv_widget_status, "Durdurmak için dokun")
                } else {
                    remoteViews.setImageViewResource(R.id.btn_record_voice, R.drawable.ic_microphone_24)
                    remoteViews.setTextViewText(R.id.tv_widget_status, "Kaydetmek için dokun")
                }
                
                // Create service intent
                val serviceIntent = Intent(context, AudioRecordingService::class.java).apply {
                    action = if (isCurrentlyRecording) {
                        AudioRecordingService.ACTION_STOP_RECORDING
                    } else {
                        AudioRecordingService.ACTION_START_RECORDING
                    }
                    putExtra("widget_id", appWidgetId)
                }

                val pendingIntent = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        PendingIntent.getForegroundService(
                            context, 
                            appWidgetId, 
                            serviceIntent,
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            } else {
                                PendingIntent.FLAG_UPDATE_CURRENT
                            }
                        )
                    } else {
                        PendingIntent.getService(
                            context, 
                            appWidgetId, 
                            serviceIntent,
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            } else {
                                PendingIntent.FLAG_UPDATE_CURRENT
                            }
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error creating service PendingIntent", e)
                    // Fallback to activity intent
                    val activityIntent = Intent(context, MainActivity::class.java)
                    PendingIntent.getActivity(
                        context, 
                        appWidgetId, 
                        activityIntent,
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        } else {
                            PendingIntent.FLAG_UPDATE_CURRENT
                        }
                    )
                }
                
                remoteViews.setOnClickPendingIntent(R.id.btn_record_voice, pendingIntent)
                Log.d(TAG, "Set recording state")
            }
            
            // Update the widget
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
            Log.d(TAG, "Widget $appWidgetId updated successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating widget $appWidgetId", e)
            
            // Create minimal fallback view
            try {
                val fallbackViews = RemoteViews(context.packageName, R.layout.widget_voice_memo)
                fallbackViews.setImageViewResource(R.id.btn_record_voice, R.drawable.ic_microphone_24)
                fallbackViews.setTextViewText(R.id.tv_widget_status, "Hata: Yeniden deneyin")
                
                // Simple activity intent for fallback
                val fallbackIntent = Intent(context, MainActivity::class.java)
                val fallbackPendingIntent = PendingIntent.getActivity(
                    context, 
                    appWidgetId, 
                    fallbackIntent,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }
                )
                fallbackViews.setOnClickPendingIntent(R.id.btn_record_voice, fallbackPendingIntent)
                
                appWidgetManager.updateAppWidget(appWidgetId, fallbackViews)
                Log.d(TAG, "Fallback widget $appWidgetId set")
                
            } catch (fallbackException: Exception) {
                Log.e(TAG, "Even fallback failed for widget $appWidgetId", fallbackException)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: ${intent.action}")
        
        try {
            when (intent.action) {
                AudioRecordingService.ACTION_UPDATE_WIDGET -> {
                    val isRecording = intent.getBooleanExtra("is_recording", false)
                    Log.d(TAG, "Received widget update, recording: $isRecording")
                    setRecording(context, isRecording)
                    
                    // Update all widgets
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val componentName = ComponentName(context, VoiceMemoWidgetProvider::class.java)
                    val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                    onUpdate(context, appWidgetManager, appWidgetIds)
                }
                else -> {
                    super.onReceive(context, intent)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onReceive", e)
            super.onReceive(context, intent)
        }
    }

    override fun onEnabled(context: Context) {
        Log.d(TAG, "Widget enabled")
        super.onEnabled(context)
    }

    override fun onDisabled(context: Context) {
        Log.d(TAG, "Widget disabled")
        super.onDisabled(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        Log.d(TAG, "Widgets deleted: ${appWidgetIds.joinToString()}")
        super.onDeleted(context, appWidgetIds)
    }
}