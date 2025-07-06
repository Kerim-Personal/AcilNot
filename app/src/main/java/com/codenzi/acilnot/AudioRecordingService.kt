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
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.Manifest
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

    // Servis içinde MediaRecorder örneğini tutacağız.
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
            ACTION_START_RECORDING -> {
                startRecording()
            }
            ACTION_STOP_RECORDING -> {
                stopRecordingAndSave()
            }
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (mediaRecorder != null) return // Zaten kayıt yapılıyorsa tekrar başlatma

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Mikrofon izni gerekli", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            audioFile = createAudioFile()
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder

            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification())
            updateWidgetState(true)
            Toast.makeText(this, "Kayıt başladı...", Toast.LENGTH_SHORT).show()

        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Kayıt başlatılamadı.", Toast.LENGTH_SHORT).show()
            cleanup()
        }
    }

    private fun stopRecordingAndSave() {
        if (mediaRecorder == null) return // Durdurulacak bir kayıt yoksa bir şey yapma

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaRecorder = null
            saveAudioNote()
            Toast.makeText(this, "Sesli not kaydedildi", Toast.LENGTH_SHORT).show()
            cleanup()
        }
    }

    private fun cleanup() {
        updateWidgetState(false)
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
        if (audioFile == null || !audioFile!!.exists() || audioFile!!.length() == 0L) {
            return
        }
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
    }

    private fun createAudioFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File? = getExternalFilesDir("AudioNotes")
        storageDir?.mkdirs()
        return File.createTempFile("AUDIO_${timeStamp}_", ".mp3", storageDir)
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, AudioRecordingService::class.java).apply {
            action = ACTION_STOP_RECORDING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
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