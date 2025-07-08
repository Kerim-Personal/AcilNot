package com.codenzi.snapnote

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.preference.PreferenceManager

class CameraWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val remoteViews = RemoteViews(context.packageName, R.layout.widget_camera)

        // *** DEĞİŞİKLİK: Intent artık ComponentName ile oluşturuluyor ***
        // Bu yöntem, "Unresolved reference" hatasını düzeltir.
        val intent = Intent().apply {
            val component = ComponentName("com.codenzi.snapnote", "com.codenzi.snapnote.WidgetCameraNoteActivity")
            component.let { setComponent(it) }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val backgroundDrawableName = sharedPrefs.getString("widget_background_selection", "widget_background")
        val backgroundResId = context.resources.getIdentifier(
            backgroundDrawableName, "drawable", context.packageName
        )
        remoteViews.setInt(R.id.camera_widget_container, "setBackgroundResource", if (backgroundResId != 0) backgroundResId else R.drawable.widget_background)

        remoteViews.setOnClickPendingIntent(R.id.btn_take_photo, pendingIntent)
        appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
    }
}