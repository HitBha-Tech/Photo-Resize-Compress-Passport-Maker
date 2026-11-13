package com.hitbha.photo.utils

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleUtils {

    private val SUPPORTED_LANGUAGES = setOf(
        "ar", "bn", "zh", "en", "fr", "de", "gu",
        "hi", "id", "it", "ja", "ko", "mr",
        "pt", "ru", "es", "ta", "te", "tr", "ur"
    )

    private fun safeLocale(candidate: String): Locale {
        val lang = candidate.trim().lowercase()
        if (lang.isEmpty()) return Locale.forLanguageTag("en")
        val loc = Locale.forLanguageTag(lang)
        return if (loc.language in SUPPORTED_LANGUAGES) loc else Locale.forLanguageTag("en")
    }

    fun applyLocale(context: Context): Context {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val lang = prefs.getString("app_language", "en") ?: "en"
        val locale = safeLocale(lang)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
