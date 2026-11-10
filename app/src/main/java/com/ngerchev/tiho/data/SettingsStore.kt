package com.ngerchev.tiho.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class AppSettings(
    /** Language the user speaks: "bg-BG" or "en-US". */
    val languageTag: String,
    val preferOnDevice: Boolean = true,
    val keepFor24h: Boolean = true,
    val spokenPunctuation: Boolean = true
)

/** Four simple preferences in private SharedPreferences. */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        prefs.edit()
            .putString(KEY_LANGUAGE, next.languageTag)
            .putBoolean(KEY_ON_DEVICE, next.preferOnDevice)
            .putBoolean(KEY_KEEP, next.keepFor24h)
            .putBoolean(KEY_PUNCTUATION, next.spokenPunctuation)
            .apply()
    }

    private fun read(): AppSettings {
        val defaultLanguage = if (Locale.getDefault().language == "bg") LANG_BG else LANG_EN
        return AppSettings(
            languageTag = prefs.getString(KEY_LANGUAGE, null) ?: defaultLanguage,
            preferOnDevice = prefs.getBoolean(KEY_ON_DEVICE, true),
            keepFor24h = prefs.getBoolean(KEY_KEEP, true),
            spokenPunctuation = prefs.getBoolean(KEY_PUNCTUATION, true)
        )
    }

    companion object {
        const val LANG_BG = "bg-BG"
        const val LANG_EN = "en-US"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_ON_DEVICE = "prefer_on_device"
        private const val KEY_KEEP = "keep_24h"
        private const val KEY_PUNCTUATION = "spoken_punctuation"
    }
}
