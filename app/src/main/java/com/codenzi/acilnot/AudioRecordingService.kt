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
import android.widget.RemoteViews
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

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isCurrentlyRecording = false

    companion object {
        const val ACTION_START_RECORDING = "com.codenzi.acilnot.action.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.codenzi.acilnot.action.STOP_RECORDING"
        const val ACTION_UPDATE_WIDGET = "com.codenzi.acilnot.action.UPDATE_WIDGET"
        private const val NOTIFICATION_CHANNEL_ID = "AudioRecordingChannel"
        private const val NOTIFICATION_ID = 12345
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            // Check for permission first
            if (ContextCompat.checkSelfPermission(
                    this, 
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Mikrofon izni gerekli", Toast.LENGTH_SHORT).show()
                stopSelf()
                return START_NOT_STICKY
            }
            
            when (intent?.action) {
                ACTION_START_RECORDING -> {
                    if (!isCurrentlyRecording) {
                        startRecording()
                    }
                }
                ACTION_STOP_RECORDING -> {
                    if (isCurrentlyRecording) {
                        stopRecordingAndSave()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Ses kayıt servisinde hata oluştu", Toast.LENGTH_SHORT).show()
            stopSelf()
        }
        return START_STICKY
    }

    private fun startRecording() {
        try {
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
                try {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setOutputFile(audioFile?.absolutePath)
                    prepare()
                    start()
                    isCurrentlyRecording = true
                    Toast.makeText(this@AudioRecordingService, "Kayıt başladı...", Toast.LENGTH_SHORT).show()
                    updateWidgetState(true)
                } catch (e: IOException) {
                    e.printStackTrace()
                    Toast.makeText(this@AudioRecordingService, "Kayıt başlatılamadı: ${e.message}", Toast.LENGTH_SHORT).show()
                    cleanup()
                } catch (e: SecurityException) {
                    e.printStackTrace()
                    Toast.makeText(this@AudioRecordingService, "Mikrofon izni gerekli", Toast.LENGTH_SHORT).show()
                    cleanup()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this@AudioRecordingService, "Kayıt hatası: ${e.message}", Toast.LENGTH_SHORT).show()
                    cleanup()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Kayıt başlatılamadı", Toast.LENGTH_SHORT).show()
            cleanup()
        }
    }

    private fun stopRecordingAndSave() {
        try {
            mediaRecorder?.apply {
                try {
                    stop()
                    release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            mediaRecorder = null
            isCurrentlyRecording = false
            
            if (audioFile?.exists() == true && audioFile?.length() ?: 0 > 0) {
                saveAudioNote()
                Toast.makeText(this, "Sesli not kaydedildi", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Kayıt dosyası bulunamadı", Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Kayıt kaydedilirken hata oluştu", Toast.LENGTH_SHORT).show()
        } finally {
            cleanup()
        }
    }

    private fun cleanup() {
        try {
            mediaRecorder?.release()
            mediaRecorder = null
            isCurrentlyRecording = false
            stopForeground(true)
            stopSelf()
            updateWidgetState(false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveAudioNote() {
        try {
            val noteDao = NoteDatabase.getDatabase(this).noteDao()
            val title = "Sesli Not - ${formatDate(System.currentTimeMillis())}"
            val contentJson = Gson().toJson(NoteContent(
                text = "",
                checklist = mutableListOf(),
                audioFilePath = audioFile?.absolutePath
            ))

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    noteDao.insert(Note(
                        title = title,
                        content = contentJson,
                        createdAt = System.currentTimeMillis()
                    ))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
            .addAction(R.drawable.ic_stop_24, "Durdur ve Kaydet", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateWidgetState(isRecording: Boolean) {
        try {
            // Update widget provider state
            VoiceMemoWidgetProvider.setRecording(this, isRecording)
            
            // Send broadcast to update widget UI
            val updateIntent = Intent(ACTION_UPDATE_WIDGET)
            updateIntent.putExtra("is_recording", isRecording)
            sendBroadcast(updateIntent)
            
            // Also update widget UI directly
            updateWidgetUi(isRecording)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateWidgetUi(isRecording: Boolean) {
        try {
            val context = applicationContext
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, VoiceMemoWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            for (appWidgetId in appWidgetIds) {
                val remoteViews = RemoteViews(context.packageName, R.layout.widget_voice_memo)
                if (isRecording) {
                    remoteViews.setImageViewResource(R.id.btn_record_voice, R.drawable.ic_stop_24)
                    remoteViews.setTextViewText(R.id.tv_widget_status, "Durdurmak için dokun")
                } else {
                    remoteViews.setImageViewResource(R.id.btn_record_voice, R.drawable.ic_microphone_24)
                    remoteViews.setTextViewText(R.id.tv_widget_status, "Kaydetmek için dokun")
                }
                appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}