package com.codenzi.snapnote

import android.app.Application
import androidx.work.*
import dagger.hilt.android.HiltAndroidApp // Bu satırı ekleyin
import java.util.concurrent.TimeUnit

@HiltAndroidApp // Hilt'i etkinleştirmek için bu anotasyonu ekliyoruz
class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Uygulama ilk açıldığında periyodik temizleme görevini başlat
        scheduleTrashCleanup()
    }

    private fun scheduleTrashCleanup() {
        // Görevin çalışma koşullarını belirle (örn: internet gerekmesin)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresCharging(false)
            .build()

        // Günde bir kez çalışacak şekilde periyodik bir istek oluştur
        val repeatingRequest = PeriodicWorkRequestBuilder<TrashCleanupWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        // WorkManager'a bu görevi "benzersiz" bir isimle kaydet
        // Bu, görevin birden fazla kez programlanmasını engeller
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "trashCleanupWork",
            ExistingPeriodicWorkPolicy.KEEP, // Eğer görev zaten varsa, eskisini koru ve yenisini ekleme
            repeatingRequest
        )
    }
}