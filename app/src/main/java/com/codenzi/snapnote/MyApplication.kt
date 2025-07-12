package com.codenzi.snapnote

import android.app.Application
import androidx.work.*
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // PasswordManager'ı uygulama başlatılırken YALNIZCA BİR KEZ başlat.
        // Bu, çökme sorununu engelleyen en kritik adımdır.
        PasswordManager.initialize(applicationContext)
        scheduleTrashCleanup()
    }

    private fun scheduleTrashCleanup() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresCharging(false)
            .build()

        val repeatingRequest = PeriodicWorkRequestBuilder<TrashCleanupWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "trashCleanupWork",
            ExistingPeriodicWorkPolicy.KEEP,
            repeatingRequest
        )
    }
}