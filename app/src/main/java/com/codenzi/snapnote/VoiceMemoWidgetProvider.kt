package com.codenzi.snapnote

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.edit
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

    // updateAppWidget'ı companion object içine alarak
    // hem Provider içinden hem de dışarıdan (örn. SettingsActivity) erişilebilir kılıyoruz.
    companion object {
        private const val PREFS_NAME = "voice_memo_widget_prefs"
        private const val PREF_WIDGET_STATE = "widget_state"

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_voice_memo)
            val currentState = getWidgetState(context)

            // DÜZELTME: Kaynaklara daha verimli erişim sağlandı.
            val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
            val backgroundDrawableName = sharedPrefs.getString("widget_background_selection", "widget_background")
            val backgroundResId = getBackgroundResource(backgroundDrawableName)
            remoteViews.setInt(R.id.voice_widget_container, "setBackgroundResource", backgroundResId)

            // Widget'ın görünümünü mevcut durumuna göre ayarla
            when (currentState) {
                WidgetState.IDLE -> {
                    remoteViews.setImageViewResource(R.id.btn_record_voice, R.drawable.ic_microphone_red_24)
                    remoteViews.setTextViewText(R.id.tv_widget_status, context.getString(R.string.tap_to_record))
                    remoteViews.setViewVisibility(R.id.tv_widget_timer, View.GONE)
                    remoteViews.setTextViewText(R.id.tv_widget_timer, "00:00")
                }
                WidgetState.RECORDING -> {
                    remoteViews.setImageViewResource(R.id.btn_record_voice, R.drawable.ic_stop_24)
                    remoteViews.setTextViewText(R.id.tv_widget_status, context.getString(R.string.tap_to_stop))
                    remoteViews.setViewVisibility(R.id.tv_widget_timer, View.VISIBLE)
                }
                WidgetState.SAVED -> {
                    remoteViews.setImageViewResource(R.id.btn_record_voice, R.drawable.ic_microphone_red_24)
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

        fun getWidgetState(context: Context): WidgetState {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val stateName = prefs.getString(PREF_WIDGET_STATE, WidgetState.IDLE.name)
            return try {
                WidgetState.valueOf(stateName!!)
            } catch (e: Exception) {
                WidgetState.IDLE
            }
        }

        fun setWidgetState(context: Context, state: WidgetState) {
            // DÜZELTME: KTX uzantı fonksiyonu kullanıldı.
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
                putString(PREF_WIDGET_STATE, state.name)
            }

            // Widget'ı güncellemek için broadcast gönder.
            val intent = Intent(context, VoiceMemoWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, VoiceMemoWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
            }
            context.sendBroadcast(intent)
        }

        /**
         * SharedPreferences'tan gelen string key'e karşılık gelen drawable resource ID'sini döndürür.
         */
        private fun getBackgroundResource(name: String?): Int {
            return when (name) {
                "widget_background" -> R.drawable.widget_background
                "bg1" -> R.drawable.bg1
                "bg2" -> R.drawable.bg2
                "bg3" -> R.drawable.bg3
                "bg4" -> R.drawable.bg4
                "bg5" -> R.drawable.bg5
                "bg6" -> R.drawable.bg6
                "bg7" -> R.drawable.bg7
                "bg8" -> R.drawable.bg8
                "bg9" -> R.drawable.bg9
                "bg10" -> R.drawable.bg10
                else -> R.drawable.widget_background // Varsayılan
            }
        }
    }
}