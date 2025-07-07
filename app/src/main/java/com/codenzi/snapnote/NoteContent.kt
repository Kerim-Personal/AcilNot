package com.codenzi.snapnote

data class NoteContent(
    var text: String,
    var checklist: MutableList<ChecklistItem>,
    // YENİ: Ses kaydı dosyasının yolunu tutacak alan
    val audioFilePath: String? = null
)