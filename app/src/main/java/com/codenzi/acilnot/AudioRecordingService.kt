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
import android.util.Log
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
        private const val TAG = "AudioRecordingService"
        const val ACTION_START_RECORDING = "com.codenzi.acilnot.action.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.codenzi.acilnot.action.STOP_RECORDING"
        const val ACTION_UPDATE_WIDGET = "com.codenzi.acilnot.action.UPDATE_WIDGET"
        private const val NOTIFICATION_CHANNEL_ID = "AudioRecordingChannel"
        private const val NOTIFICATION_ID = 12345
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        
        try {
            // Check for permission first
            if (ContextCompat.checkSelfPermission(
                    this, 
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "RECORD_AUDIO permission not granted")
                Toast.makeText(this, "Mikrofon izni gerekli", Toast.LENGTH_SHORT).show()
                stopSelf()
                return START_NOT_STICKY
            }
            
            when (intent?.action) {
                ACTION_START_RECORDING -> {
                    Log.d(TAG, "Starting recording")
                    if (!isCurrentlyRecording) {
                        startRecording()
                    } else {
                        Log.w(TAG, "Already recording")
                    }
                }
                ACTION_STOP_RECORDING -> {
                    Log.d(TAG, "Stopping recording")
                    if (isCurrentlyRecording) {
                        stopRecordingAndSave()
                    } else {
                        Log.w(TAG, "Not currently recording")
                        stopSelf()
                    }
                }
                else -> {
                    Log.w(TAG, "Unknown action: ${intent?.action}")
                    stopSelf()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand", e)
            Toast.makeText(this, "Ses kayıt servisinde hata oluştu", Toast.LENGTH_SHORT).show()
            cleanup()
        }
        return START_STICKY
    }

    private fun startRecording() {
        try {
            Log.d(TAG, "Creating notification channel and starting foreground")
            createNotificationChannel()
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)

            Log.d(TAG, "Creating audio file")
            audioFile = createAudioFile()
            
            Log.d(TAG, "Creating MediaRecorder")
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            
            mediaRecorder?.apply {
                try {
                    Log.d(TAG, "Configuring MediaRecorder")
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setOutputFile(audioFile?.absolutePath)
                    
                    Log.d(TAG, "Preparing MediaRecorder")
                    prepare()
                    
                    Log.d(TAG, "Starting MediaRecorder")
                    start()
                    
                    isCurrentlyRecording = true
                    Toast.makeText(this@AudioRecordingService, "Kayıt başladı...", Toast.LENGTH_SHORT).show()
                    updateWidgetState(true)
                    Log.d(TAG, "Recording started successfully")
                    
                } catch (e: IOException) {
                    Log.e(TAG, "IOException during recording setup", e)
                    Toast.makeText(this@AudioRecordingService, "Kayıt başlatılamadı: ${e.message}", Toast.LENGTH_SHORT).show()
                    cleanup()
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException during recording setup", e)
                    Toast.makeText(this@AudioRecordingService, "Mikrofon izni gerekli", Toast.LENGTH_SHORT).show()
                    cleanup()
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error during recording setup", e)
                    Toast.makeText(this@AudioRecordingService, "Kayıt hatası: ${e.message}", Toast.LENGTH_SHORT).show()
                    cleanup()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in startRecording", e)
            Toast.makeText(this, "Kayıt başlatılamadı", Toast.LENGTH_SHORT).show()
            cleanup()
        }
    }

    private fun stopRecordingAndSave() {
        Log.d(TAG, "Stopping recording and saving")
        try {
            mediaRecorder?.apply {
                try {
                    Log.d(TAG, "Stopping MediaRecorder")
                    stop()
                    Log.d(TAG, "Releasing MediaRecorder")
                    release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping MediaRecorder", e)
                }
            }
            mediaRecorder = null
            isCurrentlyRecording = false
            
            if (audioFile?.exists() == true && audioFile?.length() ?: 0 > 0) {
                Log.d(TAG, "Audio file exists, saving note")
                saveAudioNote()
                Toast.makeText(this, "Sesli not kaydedildi", Toast.LENGTH_SHORT).show()
            } else {
                Log.w(TAG, "Audio file not found or empty")
                Toast.makeText(this, "Kayıt dosyası bulunamadı", Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in stopRecordingAndSave", e)
            Toast.makeText(this, "Kayıt kaydedilirken hata oluştu", Toast.LENGTH_SHORT).show()
        } finally {
            cleanup()
        }
    }

    private fun cleanup() {
        Log.d(TAG, "Cleaning up service")
        try {
            mediaRecorder?.release()
            mediaRecorder = null
            isCurrentlyRecording = false
            stopForeground(true)
            updateWidgetState(false)
            stopSelf()
            Log.d(TAG, "Cleanup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }

    private fun saveAudioNote() {
        try {
            Log.d(TAG, "Saving audio note to database")
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
                    Log.d(TAG, "Audio note saved successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving audio note to database", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in saveAudioNote", e)
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
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
        Log.d(TAG, "Updating widget state: isRecording=$isRecording")
        try {
            // Update widget provider state
            VoiceMemoWidgetProvider.setRecording(this, isRecording)
            
            // Send broadcast to update widget UI
            val updateIntent = Intent(ACTION_UPDATE_WIDGET)
            updateIntent.putExtra("is_recording", isRecording)
            sendBroadcast(updateIntent)
            
            // Also update widget UI directly
            updateWidgetUi(isRecording)
            Log.d(TAG, "Widget state updated successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating widget state", e)
        }
    }

    private fun updateWidgetUi(isRecording: Boolean) {
        try {
            val context = applicationContext
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, VoiceMemoWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            Log.d(TAG, "Updating ${appWidgetIds.size} widgets")
            
            for (appWidgetId in appWidgetIds) {
                val remoteViews = RemoteViews(context.packageName, R.layout.widget_voice_memo)
                if (isRecording) {
                    remoteViews.setImageViewResource(R.id.btn_record_voice, R.drawable.ic_stop_24)
                    remoteViews.setTextViewText(R.id.tv_widget_status, "Dur")
                } else {
                    remoteViews.setImageViewResource(R.id.btn_record_voice, R.drawable.ic_microphone_24)
                    remoteViews.setTextViewText(R.id.tv_widget_status, "Dokun")
                }
                appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating widget UI", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}