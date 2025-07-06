package com.codenzi.acilnot

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.preference.PreferenceManager

// Widget'ın farklı durumlarını yönetmek için bir enum sınıfı
enum class WidgetState {
    IDLE,      // Boşta, kayıt bekleniyor
    RECORDING, // Kayıt yapılıyor
    SAVED      // Kayıt yeni bitti, "Kaydedildi" durumu
}

class VoiceMemoWidgetProvider : AppWidgetProvider() {

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
        val currentState = getWidgetState(context)

        // Widget arka planını ayarla
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val backgroundDrawableName = sharedPrefs.getString("widget_background_selection", "widget_background")
        val backgroundResId = context.resources.getIdentifier(
            backgroundDrawableName, "drawable", context.packageName
        )

        if (backgroundResId != 0) {
            remoteViews.setInt(R.id.voice_widget_container, "setBackgroundResource", backgroundResId)
        } else {
            remoteViews.setInt(R.id.voice_widget_container, "setBackgroundResource", R.drawable.widget_background)
        }

        // Widget'ın görünümünü mevcut durumuna göre ayarla
        when (currentState) {
            WidgetState.IDLE -> {
                remoteViews.setImageViewResource(R.id.btn_record_voice, R.drawable.ic_microphone_red_24)
                // Değişiklik burada
                remoteViews.setTextViewText(R.id.tv_widget_status, context.getString(R.string.tap_to_record))
                remoteViews.setViewVisibility(R.id.tv_widget_timer, View.GONE)
                remoteViews.setTextViewText(R.id.tv_widget_timer, "00:00")
            }
            WidgetState.RECORDING -> {
                remoteViews.setImageViewResource(R.id.btn_record_voice, R.drawable.ic_stop_24)
                // Değişiklik burada
                remoteViews.setTextViewText(R.id.tv_widget_status, context.getString(R.string.tap_to_stop))
                remoteViews.setViewVisibility(R.id.tv_widget_timer, View.VISIBLE)
            }
            WidgetState.SAVED -> {
                remoteViews.setImageViewResource(R.id.btn_record_voice, R.drawable.ic_microphone_red_24)
                // Değişiklik burada
                remoteViews.setTextViewText(R.id.tv_widget_status, context.getString(R.string.saved))
                remoteViews.setViewVisibility(R.id.tv_widget_timer, View.GONE)
            }
        }

        // Tıklama olayını ayarla
        val intent = Intent(context, AudioRecordingService::class.java).apply {
            action = if (currentState == WidgetState.RECORDING) {
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
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        remoteViews.setOnClickPendingIntent(R.id.btn_record_voice, pendingIntent)
        appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
    }

    companion object {
        private const val PREFS_NAME = "voice_memo_widget_prefs"
        private const val PREF_WIDGET_STATE = "widget_state"

        fun getWidgetState(context: Context): WidgetState {
            val stateName = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREF_WIDGET_STATE, WidgetState.IDLE.name)
            return try { WidgetState.valueOf(stateName!!) } catch (e: Exception) { WidgetState.IDLE }
        }

        fun setWidgetState(context: Context, state: WidgetState) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(PREF_WIDGET_STATE, state.name)
                .apply()

            val intent = Intent(context, VoiceMemoWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, VoiceMemoWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
            }
            context.sendBroadcast(intent)
        }
    }
}