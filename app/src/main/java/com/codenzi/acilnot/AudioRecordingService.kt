package com.codenzi.acilnot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build // HATA 1: Eksik olan import ifadesi eklendi.
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AudioRecordingService : Service() {

    private var audioFilePath: String? = null

    companion object {
        const val ACTION_START_RECORDING = "com.codenzi.acilnot.action.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.codenzi.acilnot.action.STOP_RECORDING"
        private const val NOTIFICATION_CHANNEL_ID = "AudioRecordingChannel"
        private const val NOTIFICATION_ID = 12345
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_START_RECORDING) {
            audioFilePath = intent.getStringExtra("audio_file_path")

            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification())

            Toast.makeText(this, "Kayıt başladı...", Toast.LENGTH_SHORT).show()
            updateWidgetState(true)

        } else if (action == ACTION_STOP_RECORDING) {
            stopRecordingAndSave()
        }

        return START_NOT_STICKY
    }

    private fun stopRecordingAndSave() {
        if (audioFilePath != null) {
            saveAudioNote()
            Toast.makeText(this, "Sesli not kaydedildi", Toast.LENGTH_SHORT).show()
        }

        updateWidgetState(false)

        // HATA 3: Deprecated metod modern haliyle değiştirildi.
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateWidgetState(isRecording: Boolean) {
        VoiceMemoWidgetProvider.setRecording(this, isRecording)

        val intent = Intent(this, VoiceMemoWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            val appWidgetManager = AppWidgetManager.getInstance(this@AudioRecordingService)
            val componentName = ComponentName(this@AudioRecordingService, VoiceMemoWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
        }
        sendBroadcast(intent)
    }

    private fun saveAudioNote() {
        val noteDao = NoteDatabase.getDatabase(this).noteDao()
        val title = "Sesli Not - ${formatDate(System.currentTimeMillis())}"
        val contentJson = Gson().toJson(NoteContent(
            text = "",
            checklist = mutableListOf(),
            audioFilePath = audioFilePath
        ))

        CoroutineScope(Dispatchers.IO).launch {
            noteDao.insert(Note(
                title = title,
                content = contentJson,
                createdAt = System.currentTimeMillis()
            ))
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, AudioRecordingService::class.java).apply {
            action = ACTION_STOP_RECORDING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1, // Farklı bir istek kodu
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Acil Not")
            .setContentText("Ses kaydı yapılıyor...")
            .setSmallIcon(R.drawable.ic_microphone_24)
            .addAction(R.drawable.ic_stop_24, "Durdur", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        // HATA 2: Bu if kontrolü, API 26 uyarısını doğru şekilde yönetir.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Ses Kayıt Servisi",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(serviceChannel)
        }
    }

    private fun formatDate(timestamp: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))

    override fun onBind(intent: Intent?): IBinder? = null
}