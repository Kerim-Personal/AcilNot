package com.codenzi.snapnote

data class NoteContent(
    var text: String,
    var checklist: MutableList<ChecklistItem>,
    val audioFilePath: String? = null,
    val imagePath: String? = null,
    // YENİ: Resim ve ses verilerini Base64 formatında saklamak için alanlar
    var imageDataBase64: String? = null,
    var audioDataBase64: String? = null
)