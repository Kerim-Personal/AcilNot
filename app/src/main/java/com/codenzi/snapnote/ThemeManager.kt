package com.codenzi.snapnote

import android.app.Activity
import android.content.Context
import androidx.preference.PreferenceManager

object ThemeManager {
    fun applyTheme(activity: Activity) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        val colorValue = prefs.getString("color_selection", "purple") // Varsayılan mor

        val themeResId = when (colorValue) {
            "blue" -> R.style.Theme_AcilNotUygulamasi_Blue
            "green" -> R.style.Theme_AcilNotUygulamasi_Green
            "rose" -> R.style.Theme_AcilNotUygulamasi_Rose
            else -> R.style.Theme_AcilNotUygulamasi_Purple // Varsayılan
        }
        activity.setTheme(themeResId)
    }
}