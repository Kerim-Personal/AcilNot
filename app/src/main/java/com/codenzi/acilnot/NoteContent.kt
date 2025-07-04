package com.codenzi.acilnot

data class NoteContent(
    var text: String,
    var checklist: MutableList<ChecklistItem>
    // audioFilePath alanı kaldırıldı
)