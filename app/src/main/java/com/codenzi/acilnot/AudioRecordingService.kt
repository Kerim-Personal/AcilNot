package com.codenzi.acilnot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AudioRecordingService : Service() {

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null

    companion object {
        const val ACTION_START_RECORDING = "com.codenzi.acilnot.action.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.codenzi.acilnot.action.STOP_RECORDING"
        private const val NOTIFICATION_CHANNEL_ID = "AudioRecordingChannel"
        private const val NOTIFICATION_ID = 12345
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> startRecording()
            ACTION_STOP_RECORDING -> stopRecordingAndSave()
        }
        return START_STICKY
    }

    private fun startRecording() {
        createNotificationChannel()
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        audioFile = createAudioFile()
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        mediaRecorder?.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(audioFile?.absolutePath)
            try {
                prepare()
                start()
                Toast.makeText(this@AudioRecordingService, "Kayıt başladı...", Toast.LENGTH_SHORT).show()
                updateWidgetUi(isRecording = true)
            } catch (e: IOException) {
                Toast.makeText(this@AudioRecordingService, "Kayıt başlatılamadı.", Toast.LENGTH_SHORT).show()
                stopSelf()
            }
        }
    }

    private fun stopRecordingAndSave() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            // Hata olsa bile devam etmeye çalış
            e.printStackTrace()
        } finally {
            mediaRecorder = null
            saveAudioNote()
            stopForeground(true)
            stopSelf()
            updateWidgetUi(isRecording = false)
        }
    }

    private fun saveAudioNote() {
        val noteDao = NoteDatabase.getDatabase(this).noteDao()
        val title = "Sesli Not - ${formatDate(System.currentTimeMillis())}"
        val contentJson = Gson().toJson(NoteContent(
            text = "",
            checklist = mutableListOf(),
            audioFilePath = audioFile?.absolutePath
        ))

        CoroutineScope(Dispatchers.IO).launch {
            noteDao.insert(Note(
                title = title,
                content = contentJson,
                createdAt = System.currentTimeMillis()
            ))
        }
        Toast.makeText(this, "Sesli not kaydedildi.", Toast.LENGTH_SHORT).show()
    }

    private fun createAudioFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File? = getExternalFilesDir("AudioNotes")
        if (storageDir?.exists() == false) {
            storageDir.mkdirs()
        }
        return File.createTempFile("AUDIO_${timeStamp}_", ".mp3", storageDir)
    }

    private fun formatDate(timestamp: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Ses Kayıt Servisi",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, AudioRecordingService::class.java).apply {
            action = ACTION_STOP_RECORDING
        }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Acil Not")
            .setContentText("Ses kaydı yapılıyor...")
            .setSmallIcon(R.drawable.ic_microphone_24)
            .addAction(R.drawable.ic_stop_24, "Durdur ve Kaydet", stopPendingIntent)
            .build()
    }

    private fun updateWidgetUi(isRecording: Boolean) {
        val context = applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, VoiceMemoWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

        for (appWidgetId in appWidgetIds) {
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_voice_memo)
            if (isRecording) {
                remoteViews.setImageViewResource(R.id.btn_record_voice, R.drawable.ic_stop_24)
                remoteViews.setTextViewText(R.id.tv_widget_status, "Kaydediliyor...")
            } else {
                remoteViews.setImageViewResource(R.id.btn_record_voice, R.drawable.ic_microphone_24)
                remoteViews.setTextViewText(R.id.tv_widget_status, "Kaydetmek için dokun")
            }
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}