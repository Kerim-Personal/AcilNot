package com.codenzi.acilnotuygulamasi
import android.content.Context

object NoteStorage {

    private const val PREFS_NAME = "AcilNotPrefs"
    private const val NOTE_KEY = "acil_not"

    fun saveNote(context: Context, note: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(NOTE_KEY, note).apply()
    }

    fun loadNote(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(NOTE_KEY, "Henüz not yok.") ?: "Henüz not yok."
    }
}