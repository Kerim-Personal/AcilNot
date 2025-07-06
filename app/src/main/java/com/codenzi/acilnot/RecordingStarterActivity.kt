package com.codenzi.acilnot

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity // DÜZELTME: Doğru import eklendi
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bu aktivitenin tek görevi, widget'tan gelen istekle anlık olarak açılıp,
 * güvenli bir şekilde ses kaydını başlatıp, görevi AudioRecordingService'e devredip
 * kendini hemen kapatmaktır. Arayüzü yoktur.
 */
// DÜZELTME: Activity sınıfı, AppCompatActivity olarak değiştirildi.
class RecordingStarterActivity : AppCompatActivity() {

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startRecordingFlow()
            } else {
                Toast.makeText(this, "Ses kaydı için mikrofon izni gerekli.", Toast.LENGTH_LONG).show()
                finish() // İzin verilmezse aktiviteyi kapat
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // İzin kontrolü yap
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecordingFlow()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecordingFlow() {
        val isRecording = VoiceMemoWidgetProvider.isRecording(this)

        val serviceIntent = Intent(this, AudioRecordingService::class.java)

        if (isRecording) {
            // Eğer zaten bir kayıt varsa, sadece durdurma komutu gönder
            serviceIntent.action = AudioRecordingService.ACTION_STOP_RECORDING
            startService(serviceIntent)
        } else {
            // Yeni bir kayıt başlat
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
                mediaRecorder = recorder // Kaydediciyi değişkene ata

                // Servisi, kaydı devralması ve bildirimi göstermesi için başlat
                serviceIntent.action = AudioRecordingService.ACTION_START_RECORDING
                serviceIntent.putExtra("audio_file_path", audioFile?.absolutePath)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Kayıt başlatılamadı.", Toast.LENGTH_SHORT).show()
                mediaRecorder?.release()
                mediaRecorder = null
            }
        }
        // Görevini tamamlayan aktiviteyi hemen kapat
        finish()
    }

    private fun createAudioFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File? = getExternalFilesDir("AudioNotes")
        if (storageDir?.exists() == false) {
            storageDir.mkdirs()
        }
        return File.createTempFile("AUDIO_${timeStamp}_", ".mp3", storageDir)
    }
}