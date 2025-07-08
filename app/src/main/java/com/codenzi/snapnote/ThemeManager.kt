package com.codenzi.snapnote

import android.app.Activity
import android.content.Context
import androidx.preference.PreferenceManager

object ThemeManager {
    fun applyTheme(activity: Activity) {
        activity.setTheme(getThemeResId(activity))
    }

    fun getThemeResId(context: Context): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val colorValue = prefs.getString("color_selection", "purple") // Varsayılan mor

        return when (colorValue) {
            "blue" -> R.style.Theme_AcilNotUygulamasi_Blue
            "green" -> R.style.Theme_AcilNotUygulamasi_Green
            "rose" -> R.style.Theme_AcilNotUygulamasi_Rose
            else -> R.style.Theme_AcilNotUygulamasi_Purple // Varsayılan
        }
    }
}