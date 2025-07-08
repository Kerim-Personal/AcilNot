package com.codenzi.snapnote

data class NoteContent(
    var text: String,
    var checklist: MutableList<ChecklistItem>,
    val audioFilePath: String? = null,
    val imagePath: String? = null // Bu satırı ekleyin
)