package com.codenzi.snapnote

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPoint
import dagger.hilt.android.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

class TrashCleanupWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TrashCleanupWorkerEntryPoint {
        fun noteRepository(): NoteRepository
    }

    override suspend fun doWork(): Result {
        return try {
            val hiltEntryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                TrashCleanupWorkerEntryPoint::class.java
            )
            val noteRepository = hiltEntryPoint.noteRepository()
            
            // 30 günün milisaniye karşılığını hesapla
            val thirtyDaysInMillis = TimeUnit.DAYS.toMillis(30)
            // Şu anki zamandan 30 gün öncesinin zaman damgasını bul
            val thirtyDaysAgoTimestamp = System.currentTimeMillis() - thirtyDaysInMillis

            // Repository üzerinden güvenli silme işlemini çağır
            val deletedCount = noteRepository.deleteOldTrashedNotesSafely(thirtyDaysAgoTimestamp)
            
            // Silinen not sayısını logla
            android.util.Log.i("TrashCleanupWorker", "Çöp kutusundan $deletedCount eski not silindi.")

            Result.success()
        } catch (e: Exception) {
            // Hata durumunda detaylı log
            android.util.Log.e("TrashCleanupWorker", "Çöp kutusu temizleme hatası: ${e.message}", e)
            Result.failure()
        }
    }
}