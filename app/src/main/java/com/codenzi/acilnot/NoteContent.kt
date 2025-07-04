package com.codenzi.acilnot

data class NoteContent(
    var text: String,
    var checklist: MutableList<ChecklistItem>,
    var audioFilePath: String? = null // YENİ: Ses dosyası yolunu tutacak alan
)