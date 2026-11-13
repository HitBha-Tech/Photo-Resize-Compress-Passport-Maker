package com.hitbha.photo

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.google.android.gms.ads.MobileAds
import com.hitbha.photo.utils.StatePersistence
import com.hitbha.photo.utils.ThemeManager

class PhotoResizeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        StatePersistence.cleanupOldCache(this)
        applySavedLocale()
        try {
            ThemeManager.applyNightMode(this)
        } catch (t: Throwable) {
            Log.e("PhotoResizeApp", "Failed to apply night mode", t)
        }
        try {
            MobileAds.initialize(this)
        } catch (t: Throwable) {
            Log.e("PhotoResizeApp", "Failed to initialize AdMob", t)
        }
    }

    private fun applySavedLocale() {
        try {
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val lang = prefs.getString("app_language", "en") ?: "en"
            val appLocale = LocaleListCompat.forLanguageTags(lang)
            AppCompatDelegate.setApplicationLocales(appLocale)
        } catch (t: Throwable) {
            Log.e("PhotoResizeApp", "Failed to apply saved locale", t)
        }
    }
}
