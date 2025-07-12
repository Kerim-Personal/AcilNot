package com.codenzi.snapnote

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * DOĞRULAMA YÖNETİCİSİ: Uygulama durumunu ve veri bütünlüğünü kontrol eder
 */
object ValidationManager {
    
    private const val TAG = "ValidationManager"
    
    data class ValidationResult(
        val isValid: Boolean,
        val issues: List<ValidationIssue>,
        val recommendations: List<String>
    )
    
    data class ValidationIssue(
        val severity: IssueSeverity,
        val category: IssueCategory,
        val description: String,
        val solution: String
    )
    
    enum class IssueSeverity { LOW, MEDIUM, HIGH, CRITICAL }
    enum class IssueCategory { DATA_INTEGRITY, PERFORMANCE, SECURITY, USER_EXPERIENCE }
    
    /**
     * Uygulama durumunu kapsamlı olarak doğrular
     */
    suspend fun performFullValidation(
        context: Context,
        noteDao: NoteDao
    ): ValidationResult = withContext(Dispatchers.IO) {
        
        val issues = mutableListOf<ValidationIssue>()
        val recommendations = mutableListOf<String>()
        
        // Veritabanı bütünlük kontrolü
        validateDatabaseIntegrity(noteDao, issues, recommendations)
        
        // Dosya sistemi kontrolü
        validateFileSystem(context, issues, recommendations)
        
        // Performans kontrolü
        validatePerformance(noteDao, issues, recommendations)
        
        // Güvenlik kontrolü
        validateSecurity(context, issues, recommendations)
        
        val criticalIssues = issues.filter { it.severity == IssueSeverity.CRITICAL }
        
        ValidationResult(
            isValid = criticalIssues.isEmpty(),
            issues = issues,
            recommendations = recommendations
        )
    }
    
    /**
     * Veritabanı bütünlük kontrolü
     */
    private suspend fun validateDatabaseIntegrity(
        noteDao: NoteDao,
        issues: MutableList<ValidationIssue>,
        recommendations: MutableList<String>
    ) {
        try {
            val dbStatus = DatabaseSafetyManager.getDatabaseStatus(noteDao)
            
            // Çok fazla silinmiş not kontrolü
            if (dbStatus.deletedNotes > dbStatus.activeNotes * 2) {
                issues.add(ValidationIssue(
                    severity = IssueSeverity.MEDIUM,
                    category = IssueCategory.DATA_INTEGRITY,
                    description = "Çöp kutusunda çok fazla not var (${dbStatus.deletedNotes} adet)",
                    solution = "Eski notları temizlemek için DatabaseSafetyManager.safeDeleteOldTrashedNotes() kullanın"
                ))
                recommendations.add("Çöp kutusundaki eski notları düzenli olarak temizleyin")
            }
            
            // 30 günden eski notlar
            if (dbStatus.oldTrashedNotes > 0) {
                recommendations.add("${dbStatus.oldTrashedNotes} adet 30 günden eski not temizlenebilir")
            }
            
            Log.i(TAG, "Database validation completed: $dbStatus")
            
        } catch (e: Exception) {
            issues.add(ValidationIssue(
                severity = IssueSeverity.HIGH,
                category = IssueCategory.DATA_INTEGRITY,
                description = "Veritabanı bütünlük kontrolü başarısız: ${e.message}",
                solution = "Uygulama verilerini kontrol edin ve gerekirse yeniden yükleyin"
            ))
            Log.e(TAG, "Database validation failed", e)
        }
    }
    
    /**
     * Dosya sistemi kontrolü
     */
    private suspend fun validateFileSystem(
        context: Context,
        issues: MutableList<ValidationIssue>,
        recommendations: MutableList<String>
    ) {
        try {
            // Cache klasörü boyutu kontrolü
            val cacheDir = context.cacheDir
            val cacheSize = calculateDirectorySize(cacheDir)
            val cacheSizeInMB = cacheSize / (1024 * 1024)
            
            if (cacheSizeInMB > 100) {
                issues.add(ValidationIssue(
                    severity = IssueSeverity.MEDIUM,
                    category = IssueCategory.PERFORMANCE,
                    description = "Cache klasörü çok büyük: ${cacheSizeInMB}MB",
                    solution = "Cache klasörünü temizleyin"
                ))
                recommendations.add("Uygulamanın cache'ini düzenli olarak temizleyin")
            }
            
            // External files kontrolü
            val externalFilesDir = context.getExternalFilesDir(null)
            if (externalFilesDir != null) {
                val externalSize = calculateDirectorySize(externalFilesDir)
                val externalSizeInMB = externalSize / (1024 * 1024)
                
                if (externalSizeInMB > 500) {
                    issues.add(ValidationIssue(
                        severity = IssueSeverity.MEDIUM,
                        category = IssueCategory.PERFORMANCE,
                        description = "Harici dosyalar çok büyük: ${externalSizeInMB}MB",
                        solution = "Kullanılmayan medya dosyalarını temizleyin"
                    ))
                }
            }
            
            // Temp dosyaları kontrolü
            val tempFiles = context.cacheDir.listFiles()?.filter { it.name.startsWith("temp") }
            if (tempFiles != null && tempFiles.size > 10) {
                issues.add(ValidationIssue(
                    severity = IssueSeverity.LOW,
                    category = IssueCategory.PERFORMANCE,
                    description = "${tempFiles.size} adet geçici dosya bulundu",
                    solution = "Geçici dosyaları temizleyin"
                ))
            }
            
            Log.i(TAG, "File system validation completed")
            
        } catch (e: Exception) {
            issues.add(ValidationIssue(
                severity = IssueSeverity.MEDIUM,
                category = IssueCategory.PERFORMANCE,
                description = "Dosya sistemi kontrolü başarısız: ${e.message}",
                solution = "Uygulama izinlerini kontrol edin"
            ))
            Log.e(TAG, "File system validation failed", e)
        }
    }
    
    /**
     * Performans kontrolü
     */
    private suspend fun validatePerformance(
        noteDao: NoteDao,
        issues: MutableList<ValidationIssue>,
        recommendations: MutableList<String>
    ) {
        try {
            val startTime = System.currentTimeMillis()
            val dbStatus = DatabaseSafetyManager.getDatabaseStatus(noteDao)
            val queryTime = System.currentTimeMillis() - startTime
            
            if (queryTime > 1000) {
                issues.add(ValidationIssue(
                    severity = IssueSeverity.HIGH,
                    category = IssueCategory.PERFORMANCE,
                    description = "Veritabanı sorguları yavaş: ${queryTime}ms",
                    solution = "Veritabanını optimize edin veya yeniden oluşturun"
                ))
                recommendations.add("Veritabanı performansını iyileştirin")
            }
            
            if (dbStatus.totalNotes > 10000) {
                recommendations.add("Çok fazla not var (${dbStatus.totalNotes}), performans etkilenebilir")
            }
            
            Log.i(TAG, "Performance validation completed in ${queryTime}ms")
            
        } catch (e: Exception) {
            issues.add(ValidationIssue(
                severity = IssueSeverity.MEDIUM,
                category = IssueCategory.PERFORMANCE,
                description = "Performans kontrolü başarısız: ${e.message}",
                solution = "Uygulama performansını manuel olarak kontrol edin"
            ))
            Log.e(TAG, "Performance validation failed", e)
        }
    }
    
    /**
     * Güvenlik kontrolü
     */
    private suspend fun validateSecurity(
        context: Context,
        issues: MutableList<ValidationIssue>,
        recommendations: MutableList<String>
    ) {
        try {
            // Şifre durumu kontrolü
            val isPasswordSet = PasswordManager.isPasswordSet()
            
            if (!isPasswordSet) {
                recommendations.add("Güvenlik için uygulama şifresi ayarlamayı düşünün")
            }
            
            // Debug build kontrolü
            if (BuildConfig.DEBUG) {
                issues.add(ValidationIssue(
                    severity = IssueSeverity.LOW,
                    category = IssueCategory.SECURITY,
                    description = "Debug modunda çalışıyor",
                    solution = "Üretim için release build kullanın"
                ))
            }
            
            Log.i(TAG, "Security validation completed")
            
        } catch (e: Exception) {
            issues.add(ValidationIssue(
                severity = IssueSeverity.MEDIUM,
                category = IssueCategory.SECURITY,
                description = "Güvenlik kontrolü başarısız: ${e.message}",
                solution = "Güvenlik ayarlarını manuel olarak kontrol edin"
            ))
            Log.e(TAG, "Security validation failed", e)
        }
    }
    
    /**
     * Klasör boyutunu hesapla
     */
    private fun calculateDirectorySize(directory: File): Long {
        return try {
            if (!directory.exists()) return 0L
            
            var size = 0L
            directory.listFiles()?.forEach { file ->
                size += if (file.isDirectory) {
                    calculateDirectorySize(file)
                } else {
                    file.length()
                }
            }
            size
        } catch (e: Exception) {
            Log.w(TAG, "Error calculating directory size: ${e.message}")
            0L
        }
    }
    
    /**
     * Hızlı doğrulama (kritik sorunları kontrol eder)
     */
    suspend fun quickValidation(noteDao: NoteDao): Boolean = withContext(Dispatchers.IO) {
        try {
            // Basit veritabanı erişim testi
            val count = noteDao.getTotalNotesCount()
            Log.i(TAG, "Quick validation passed: $count notes found")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Quick validation failed: ${e.message}", e)
            false
        }
    }
}