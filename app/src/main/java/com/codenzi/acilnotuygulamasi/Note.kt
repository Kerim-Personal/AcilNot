package com.codenzi.acilnotuygulamasi

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "notes")
@TypeConverters(Converters::class)
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val content: String,
    val createdAt: Long,
    val modifiedAt: List<Long> = emptyList()
)