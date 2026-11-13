package com.hitbha.photo.utils

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import com.google.android.material.color.DynamicColors
import com.hitbha.photo.R

object ThemeManager {
    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_NIGHT_MODE = "night_mode"
    private const val KEY_COLOUR_MODE = "colour_mode"
    private const val KEY_THEME_COLOR = "theme_color"

    const val THEME_WHITE = "white"
    const val THEME_PURPLE = "purple"
    const val THEME_RED = "red"
    const val THEME_GREEN = "green"
    const val THEME_LIGHT_BLUE = "light_blue"
    const val THEME_YELLOW = "yellow"
    const val THEME_PINK = "pink"
    const val THEME_BROWN = "brown"
    const val THEME_ORANGE = "orange"
    const val THEME_LIGHT_GREEN = "light_green"
    const val THEME_DARK_BLUE = "dark_blue"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isNightMode(context: Context): Boolean = prefs(context).getBoolean(KEY_NIGHT_MODE, false)

    fun setNightMode(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_NIGHT_MODE, enabled).apply()
    }

    fun isColourMode(context: Context): Boolean = prefs(context).getBoolean(KEY_COLOUR_MODE, true)

    fun setColourMode(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_COLOUR_MODE, enabled).apply()
    }

    fun getThemeColor(context: Context): String = prefs(context).getString(KEY_THEME_COLOR, THEME_GREEN) ?: THEME_GREEN

    fun setThemeColor(context: Context, theme: String) {
        prefs(context).edit().putString(KEY_THEME_COLOR, theme).apply()
    }

    fun applyNightMode(context: Context) {
        try {
            AppCompatDelegate.setDefaultNightMode(
                if (isNightMode(context)) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
        } catch (_: Exception) {
        }
    }

    fun getThemeStyle(context: Context): Int {
        val isNight = try {
            isNightMode(context)
        } catch (_: Exception) {
            false
        }

        if (isNight) {
            return R.style.Theme_PhotoResize_White
        }

        val isColourMode = try {
            isColourMode(context)
        } catch (_: Exception) {
            true
        }

        val themeColor = try {
            getThemeColor(context)
        } catch (_: Exception) {
            THEME_GREEN
        }

        return when (themeColor) {
            THEME_WHITE -> if (isColourMode) R.style.Theme_PhotoResize_White_ColourMode else R.style.Theme_PhotoResize_White
            THEME_PURPLE -> if (isColourMode) R.style.Theme_PhotoResize_Purple_ColourMode else R.style.Theme_PhotoResize_Purple
            THEME_RED -> if (isColourMode) R.style.Theme_PhotoResize_Red_ColourMode else R.style.Theme_PhotoResize_Red
            THEME_GREEN -> if (isColourMode) R.style.Theme_PhotoResize_Green_ColourMode else R.style.Theme_PhotoResize_Green
            THEME_LIGHT_BLUE -> if (isColourMode) R.style.Theme_PhotoResize_LightBlue_ColourMode else R.style.Theme_PhotoResize_LightBlue
            THEME_YELLOW -> if (isColourMode) R.style.Theme_PhotoResize_Yellow_ColourMode else R.style.Theme_PhotoResize_Yellow
            THEME_PINK -> if (isColourMode) R.style.Theme_PhotoResize_Pink_ColourMode else R.style.Theme_PhotoResize_Pink
            THEME_BROWN -> if (isColourMode) R.style.Theme_PhotoResize_Brown_ColourMode else R.style.Theme_PhotoResize_Brown
            THEME_ORANGE -> if (isColourMode) R.style.Theme_PhotoResize_Orange_ColourMode else R.style.Theme_PhotoResize_Orange
            THEME_LIGHT_GREEN -> if (isColourMode) R.style.Theme_PhotoResize_LightGreen_ColourMode else R.style.Theme_PhotoResize_LightGreen
            THEME_DARK_BLUE -> if (isColourMode) R.style.Theme_PhotoResize_DarkBlue_ColourMode else R.style.Theme_PhotoResize_DarkBlue
            else -> if (isColourMode) R.style.Theme_PhotoResize_Yellow_ColourMode else R.style.Theme_PhotoResize_Yellow
        }
    }

    fun applyToActivity(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivityIfAvailable(activity)
        }
    }

    fun setupEdgeToEdge(activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activity.window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
}
