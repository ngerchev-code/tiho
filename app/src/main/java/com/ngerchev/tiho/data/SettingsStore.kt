package com.ngerchev.tiho.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
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
    EMAIL(null, "E-mail"),
    CHATGPT("com.openai.chatgpt", "ChatGPT")
}

data class AppSettings(
    /** Language the user speaks: "bg-BG" or "en-US". */
    val languageTag: String,
    val preferOnDevice: Boolean = true,
    val spokenPunctuation: Boolean = true,
    /** Capital letter, commas and closing mark are added automatically. */
    val autoPunctuation: Boolean = true,
    val retention: Retention = Retention.HOURS_24,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.GRAPHITE,
    val textSize: TextSize = TextSize.NORMAL,
    /** Every recognized phrase becomes its own message bubble, without pressing Send. */
    val autoSend: Boolean = true,
    /** What happens right after pressing Send. */
    val afterSend: SendTarget = SendTarget.NONE,
    val keepScreenOn: Boolean = true,
    val showPunctuationKeys: Boolean = true,
    val haptics: Boolean = true,
    /** One-tap send buttons shown under every message. */
    val quickTargets: List<SendTarget> = listOf(SendTarget.VIBER, SendTarget.WHATSAPP),
    /** Ready-made phrases. */
    val templates: List<String> = emptyList(),
    /** Dictated messages are translated to the other language (Bulgarian <-> English). */
    val autoTranslate: Boolean = false,
    /** Optional: punctuation by the OpenAI API, with the user's own key. */
    val aiPunctuation: Boolean = false,
    val aiKey: String = "",
    val aiModel: String = ""
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
            .putBoolean(KEY_AUTO_PUNCT, next.autoPunctuation)
            .putString(KEY_AFTER_SEND, next.afterSend.name)
            .putBoolean(KEY_SCREEN_ON, next.keepScreenOn)
            .putBoolean(KEY_PUNCT_KEYS, next.showPunctuationKeys)
            .putBoolean(KEY_HAPTICS, next.haptics)
            .putString(KEY_QUICK, next.quickTargets.joinToString(",") { it.name })
            .putString(KEY_TEMPLATES, JSONArray(next.templates).toString())
            .putBoolean(KEY_AUTO_TRANSLATE, next.autoTranslate)
            .putBoolean(KEY_AI, next.aiPunctuation)
            .putString(KEY_AI_KEY, next.aiKey)
            .putString(KEY_AI_MODEL, next.aiModel)
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
            autoSend = prefs.getBoolean(KEY_AUTO_SEND, true),
            autoPunctuation = prefs.getBoolean(KEY_AUTO_PUNCT, true),
            afterSend = enumOr(prefs.getString(KEY_AFTER_SEND, null), SendTarget.NONE),
            keepScreenOn = prefs.getBoolean(KEY_SCREEN_ON, true),
            showPunctuationKeys = prefs.getBoolean(KEY_PUNCT_KEYS, true),
            haptics = prefs.getBoolean(KEY_HAPTICS, true),
            quickTargets = readQuickTargets(),
            templates = readTemplates(defaultLanguage == LANG_BG),
            autoTranslate = prefs.getBoolean(KEY_AUTO_TRANSLATE, false),
            aiPunctuation = prefs.getBoolean(KEY_AI, false),
            aiKey = prefs.getString(KEY_AI_KEY, null) ?: "",
            aiModel = prefs.getString(KEY_AI_MODEL, null) ?: ""
        )
    }

    private fun readQuickTargets(): List<SendTarget> {
        val saved = prefs.getString(KEY_QUICK, null)
            ?: return listOf(SendTarget.VIBER, SendTarget.WHATSAPP)
        return saved.split(',').mapNotNull { name -> SendTarget.values().firstOrNull { it.name == name } }
    }

    private fun readTemplates(bulgarian: Boolean): List<String> {
        val saved = prefs.getString(KEY_TEMPLATES, null)
        if (saved != null) {
            try {
                val array = JSONArray(saved)
                return (0 until array.length()).map { array.getString(it) }
            } catch (_: Exception) {
            }
        }
        return if (bulgarian) {
            listOf(
                "Ще закъснея с 10 минути.",
                "В среща съм, ще се обадя след малко.",
                "Благодаря, получих го.",
                "Моля, изпратете ми го на имейл.",
                "Ще ви върна обаждане до един час."
            )
        } else {
            listOf(
                "I will be 10 minutes late.",
                "I am in a meeting, I will call you shortly.",
                "Thank you, I received it.",
                "Please send it to me by e-mail.",
                "I will call you back within the hour."
            )
        }
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
        // New key in 1.3: separate bubbles became the default.
        private const val KEY_AUTO_SEND = "auto_bubbles"
        private const val KEY_AUTO_PUNCT = "auto_punctuation"
        private const val KEY_AFTER_SEND = "after_send"
        private const val KEY_SCREEN_ON = "keep_screen_on"
        private const val KEY_PUNCT_KEYS = "punctuation_keys"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_QUICK = "quick_targets"
        private const val KEY_TEMPLATES = "templates"
        private const val KEY_AUTO_TRANSLATE = "auto_translate"
        private const val KEY_AI = "ai_punctuation"
        private const val KEY_AI_KEY = "ai_key"
        private const val KEY_AI_MODEL = "ai_model"
    }
}
