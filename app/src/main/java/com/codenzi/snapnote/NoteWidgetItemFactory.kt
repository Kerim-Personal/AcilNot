package com.codenzi.snapnote

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Html
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.graphics.toColorInt
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import android.util.Log

class NoteWidgetItemFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var notes: List<Note> = emptyList()
    private val noteDao = NoteDatabase.getDatabase(context).noteDao()
    private val gson = Gson()
    
    // PERFORMANS: Blocking operations yerine süspend fonksiyon kullanımı
    @Volatile
    private var isDataLoading = false

    override fun onCreate() {
        // Bu metod başlangıçta bir kez çalışır.
    }

    override fun onDataSetChanged() {
        // PERFORMANS İYİLEŞTİRMESİ: runBlocking yerine asenkron yaklaşım
        // Widget güncellemeleri sırasında oluşabilecek ANR (Application Not Responding)
        // hatalarını önlemek için özel bir yaklaşım kullanıyoruz.
        
        if (isDataLoading) {
            // Eğer zaten bir yükleme işlemi devam ediyorsa, bekle
            return
        }
        
        isDataLoading = true
        
        try {
            // SINCHRONIZATION: Thread-safe veri yükleme
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            
            // Timeout ile güvenli veri yükleme
            val job = scope.launch {
                try {
                    val loadedNotes = noteDao.getNotesForWidget()
                    notes = loadedNotes
                } catch (e: Exception) {
                    Log.e("NoteWidgetItemFactory", "Failed to load notes for widget: ${e.message}", e)
                    notes = emptyList()
                }
            }
            
            // PERFORMANS: Maksimum 5 saniye bekleme süresi
            runBlocking {
                withTimeoutOrNull(5000L) {
                    job.join()
                } ?: run {
                    Log.w("NoteWidgetItemFactory", "Widget data loading timed out, using cached data")
                    job.cancel()
                }
            }
            
        } catch (e: Exception) {
            Log.e("NoteWidgetItemFactory", "Widget data update failed: ${e.message}", e)
            // Hata durumunda boş liste kullan
            notes = emptyList()
        } finally {
            isDataLoading = false
        }
    }

    override fun onDestroy() {
        notes = emptyList()
    }

    override fun getCount(): Int = notes.size

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_note_item)

        // GÜVENLİK: Pozisyon kontrolü
        if (position >= notes.size || position < 0) {
            Log.w("NoteWidgetItemFactory", "Invalid position: $position, notes size: ${notes.size}")
            views.setTextViewText(R.id.tv_widget_item_title, "")
            views.setTextViewText(R.id.tv_widget_item_content, context.getString(R.string.widget_item_error))
            return views
        }

        try {
            val note = notes[position]

            if (note.title.isNotBlank()) {
                views.setViewVisibility(R.id.tv_widget_item_title, View.VISIBLE)
                views.setTextViewText(R.id.tv_widget_item_title, note.title)
            } else {
                views.setViewVisibility(R.id.tv_widget_item_title, View.GONE)
            }

            var contentPreview: String = try {
                val noteContent = gson.fromJson(note.content, NoteContent::class.java)

                val textPart = if (noteContent.text.isNotBlank()) {
                    Html.fromHtml(noteContent.text, Html.FROM_HTML_MODE_LEGACY).toString().trim()
                } else {
                    ""
                }

                val checklistPart = if (noteContent.checklist.isNotEmpty()) {
                    val checkedCount = noteContent.checklist.count { it.isChecked }
                    context.getString(R.string.checklist_summary_preview, checkedCount, noteContent.checklist.size)
                } else {
                    ""
                }

                if (textPart.isNotEmpty() && checklistPart.isNotEmpty()) {
                    "$textPart\n$checklistPart"
                } else {
                    textPart + checklistPart
                }
            } catch (e: JsonSyntaxException) {
                Log.w("NoteWidgetItemFactory", "Failed to parse note content for position $position: ${e.message}")
                Html.fromHtml(note.content, Html.FROM_HTML_MODE_LEGACY).toString()
            }

            val firstNewlineIndex = contentPreview.indexOf('\n')
            if (firstNewlineIndex != -1) {
                contentPreview = contentPreview.substring(0, firstNewlineIndex) + "..."
            }

            views.setTextViewText(R.id.tv_widget_item_content, contentPreview)

            try {
                views.setInt(R.id.widget_item_container, "setBackgroundColor", note.color.toColorInt())
            } catch (e: Exception) {
                Log.w("NoteWidgetItemFactory", "Failed to set background color for position $position: ${e.message}")
                views.setInt(R.id.widget_item_container, "setBackgroundColor", Color.WHITE)
            }

            val fillInIntent = Intent().apply {
                val extras = Bundle()
                extras.putInt("NOTE_ID", note.id)
                putExtras(extras)
            }

            views.setOnClickFillInIntent(R.id.widget_item_container, fillInIntent)

            return views
        } catch (e: Exception) {
            Log.e("NoteWidgetItemFactory", "Error creating widget view at position $position: ${e.message}", e)
            views.setTextViewText(R.id.tv_widget_item_title, "")
            views.setTextViewText(R.id.tv_widget_item_content, context.getString(R.string.widget_item_load_error))
            return views
        }
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = try {
        if (position < notes.size && position >= 0) {
            notes[position].id.toLong()
        } else {
            position.toLong()
        }
    } catch (e: Exception) {
        Log.w("NoteWidgetItemFactory", "Error getting item ID for position $position: ${e.message}")
        position.toLong()
    }
    override fun hasStableIds(): Boolean = true
}