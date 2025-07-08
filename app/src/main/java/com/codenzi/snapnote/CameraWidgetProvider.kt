package com.codenzi.snapnote

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
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
        val intent = Intent(context, CameraNoteActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // Widget arka planını ayarla
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val backgroundDrawableName = sharedPrefs.getString("widget_background_selection", "widget_background")
        val backgroundResId = context.resources.getIdentifier(
            backgroundDrawableName, "drawable", context.packageName
        )

        if (backgroundResId != 0) {
            remoteViews.setInt(R.id.camera_widget_container, "setBackgroundResource", backgroundResId)
        } else {
            remoteViews.setInt(R.id.camera_widget_container, "setBackgroundResource", R.drawable.widget_background)
        }

        remoteViews.setOnClickPendingIntent(R.id.btn_take_photo, pendingIntent)
        appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
    }
}