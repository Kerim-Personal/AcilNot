package com.codenzi.acilnot

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "notes")
@TypeConverters(Converters::class)
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    // YENİ EKLENEN SATIR: Başlık alanı
    val title: String,
    val content: String,
    val createdAt: Long,
    val modifiedAt: List<Long> = emptyList(),
    val color: String = "#FFECEFF1" // Varsayılan renk pastel gri
)