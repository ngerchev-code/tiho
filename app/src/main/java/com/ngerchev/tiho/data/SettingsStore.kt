package com.ngerchev.tiho.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class Accent { GRAPHITE, BLUE, GREEN, VIOLET }

enum class TextSize(val scale: Float) { SMALL(0.9f), NORMAL(1f), LARGE(1.2f) }

/** How long messages are kept on the phone. SESSION = memory only, gone when the app closes. */
enum class Retention(val millis: Long, val persist: Boolean) {
    SESSION(24L * 60 * 60 * 1000, false),
    HOUR_1(60L * 60 * 1000, true),
    HOURS_24(24L * 60 * 60 * 1000, true),
    DAYS_7(7L * 24 * 60 * 60 * 1000, true)
}

/** Where a message can be handed to. The user picks the actual contact inside that app. */
enum class SendTarget(val packageName: String?, val title: String?) {
    NONE(null, null),
    SHARE_MENU(null, null),
    VIBER("com.viber.voip", "Viber"),
    WHATSAPP("com.whatsapp", "WhatsApp"),
    TELEGRAM("org.telegram.messenger", "Telegram"),
    MESSENGER("com.facebook.orca", "Messenger"),
    CHATGPT("com.openai.chatgpt", "ChatGPT")
}

data class AppSettings(
    /** Language the user speaks: "bg-BG" or "en-US". */
    val languageTag: String,
    val preferOnDevice: Boolean = true,
    val spokenPunctuation: Boolean = true,
    val retention: Retention = Retention.HOURS_24,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.GRAPHITE,
    val textSize: TextSize = TextSize.NORMAL,
    /** Every recognized phrase becomes a message at once, without pressing Send. */
    val autoSend: Boolean = false,
    /** What happens right after pressing Send. */
    val afterSend: SendTarget = SendTarget.NONE,
    val keepScreenOn: Boolean = true,
    val showPunctuationKeys: Boolean = true,
    val haptics: Boolean = true
)

/** A handful of simple preferences in private SharedPreferences. */
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
            .putBoolean(KEY_PUNCTUATION, next.spokenPunctuation)
            .putString(KEY_RETENTION, next.retention.name)
            .putString(KEY_THEME, next.themeMode.name)
            .putString(KEY_ACCENT, next.accent.name)
            .putString(KEY_TEXT_SIZE, next.textSize.name)
            .putBoolean(KEY_AUTO_SEND, next.autoSend)
            .putString(KEY_AFTER_SEND, next.afterSend.name)
            .putBoolean(KEY_SCREEN_ON, next.keepScreenOn)
            .putBoolean(KEY_PUNCT_KEYS, next.showPunctuationKeys)
            .putBoolean(KEY_HAPTICS, next.haptics)
            .apply()
    }

    private fun read(): AppSettings {
        val defaultLanguage = if (Locale.getDefault().language == "bg") LANG_BG else LANG_EN
        // Version 1.0 had a simple on/off switch for keeping messages.
        val legacyKeep = prefs.getBoolean(KEY_LEGACY_KEEP, true)
        return AppSettings(
            languageTag = prefs.getString(KEY_LANGUAGE, null) ?: defaultLanguage,
            preferOnDevice = prefs.getBoolean(KEY_ON_DEVICE, true),
            spokenPunctuation = prefs.getBoolean(KEY_PUNCTUATION, true),
            retention = enumOr(
                prefs.getString(KEY_RETENTION, null),
                if (legacyKeep) Retention.HOURS_24 else Retention.SESSION
            ),
            themeMode = enumOr(prefs.getString(KEY_THEME, null), ThemeMode.SYSTEM),
            accent = enumOr(prefs.getString(KEY_ACCENT, null), Accent.GRAPHITE),
            textSize = enumOr(prefs.getString(KEY_TEXT_SIZE, null), TextSize.NORMAL),
            autoSend = prefs.getBoolean(KEY_AUTO_SEND, false),
            afterSend = enumOr(prefs.getString(KEY_AFTER_SEND, null), SendTarget.NONE),
            keepScreenOn = prefs.getBoolean(KEY_SCREEN_ON, true),
            showPunctuationKeys = prefs.getBoolean(KEY_PUNCT_KEYS, true),
            haptics = prefs.getBoolean(KEY_HAPTICS, true)
        )
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fallback

    companion object {
        const val LANG_BG = "bg-BG"
        const val LANG_EN = "en-US"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_ON_DEVICE = "prefer_on_device"
        private const val KEY_LEGACY_KEEP = "keep_24h"
        private const val KEY_PUNCTUATION = "spoken_punctuation"
        private const val KEY_RETENTION = "retention"
        private const val KEY_THEME = "theme"
        private const val KEY_ACCENT = "accent"
        private const val KEY_TEXT_SIZE = "text_size"
        private const val KEY_AUTO_SEND = "auto_send"
        private const val KEY_AFTER_SEND = "after_send"
        private const val KEY_SCREEN_ON = "keep_screen_on"
        private const val KEY_PUNCT_KEYS = "punctuation_keys"
        private const val KEY_HAPTICS = "haptics"
    }
}
