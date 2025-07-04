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

class VoiceNoteRecordingService : Service() {

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private lateinit var noteDao: NoteDao
    private val gson = Gson()

    companion object {
        const val ACTION_START_RECORDING = "com.codenzi.acilnot.action.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.codenzi.acilnot.action.STOP_RECORDING"
        const val ACTION_CANCEL_RECORDING = "com.codenzi.acilnot.action.CANCEL_RECORDING"
        private const val NOTIFICATION_CHANNEL_ID = "VoiceNoteRecordingChannel"
        private const val NOTIFICATION_ID = 12345
    }

    override fun onCreate() {
        super.onCreate()
        noteDao = NoteDatabase.getDatabase(this).noteDao()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> startRecording()
            ACTION_STOP_RECORDING -> stopAndSaveRecording()
            ACTION_CANCEL_RECORDING -> cancelRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        val outputDir = File(filesDir, "records")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        audioFile = File(outputDir, "AUDIO_$timestamp.mp3")

        mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(audioFile?.absolutePath)
            try {
                prepare()
                start()
                startForeground(NOTIFICATION_ID, createNotification())
                updateWidgetToRecordingState()
            } catch (e: IOException) {
                // Hata yönetimi
                stopSelf()
            }
        }
    }

    private fun stopAndSaveRecording() {
        mediaRecorder?.apply {
            stop()
            release()
        }
        mediaRecorder = null

        audioFile?.let { file ->
            CoroutineScope(Dispatchers.IO).launch {
                val title = "Sesli Not - ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}"
                val noteContent = NoteContent(text = "", checklist = mutableListOf(), audioFilePath = file.absolutePath)
                val jsonContent = gson.toJson(noteContent)

                val newNote = Note(
                    title = title,
                    content = jsonContent,
                    createdAt = System.currentTimeMillis()
                )
                noteDao.insert(newNote)
                updateAllWidgets()
            }
        }
        stopForeground(true)
        updateWidgetToIdleState()
        stopSelf()
    }

    private fun cancelRecording() {
        mediaRecorder?.apply {
            stop()
            release()
        }
        mediaRecorder = null
        audioFile?.delete()
        audioFile = null

        stopForeground(true)
        updateWidgetToIdleState()
        stopSelf()
    }

    private fun updateWidgetToRecordingState() {
        val remoteViews = RemoteViews(packageName, R.layout.voice_note_widget_recording).apply {
            setOnClickPendingIntent(R.id.btn_stop_recording, createPendingIntent(ACTION_STOP_RECORDING))
            setOnClickPendingIntent(R.id.btn_cancel_recording, createPendingIntent(ACTION_CANCEL_RECORDING))
        }
        updateAllVoiceWidgets(remoteViews)
    }

    private fun updateWidgetToIdleState() {
        val remoteViews = RemoteViews(packageName, R.layout.voice_note_widget_idle).apply {
            setOnClickPendingIntent(R.id.btn_start_recording, createPendingIntent(ACTION_START_RECORDING))
        }
        updateAllVoiceWidgets(remoteViews)
    }

    private fun updateAllVoiceWidgets(remoteViews: RemoteViews) {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, VoiceNoteWidgetProvider::class.java)
        appWidgetManager.getAppWidgetIds(componentName).forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        }
    }

    private fun updateAllWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val noteWidgetComponent = ComponentName(this, NoteWidgetProvider::class.java)
        appWidgetManager.getAppWidgetIds(noteWidgetComponent).forEach {
            appWidgetManager.notifyAppWidgetViewDataChanged(it, R.id.lv_widget_notes)
        }
    }

    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, VoiceNoteRecordingService::class.java).apply {
            this.action = action
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getService(this, 0, intent, flags)
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Sesli Not")
            .setContentText("Kayıt yapılıyor...")
            .setSmallIcon(R.drawable.ic_microphone_24)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Sesli Not Kayıt Servisi",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}