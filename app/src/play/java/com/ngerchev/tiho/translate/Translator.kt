package com.ngerchev.tiho.translate

/** Google Play edition: no translation, because this edition has no internet permission. */
object Translator {
    const val ENABLED = false

    @Suppress("UNUSED_PARAMETER", "RedundantSuspendModifier")
    suspend fun translate(text: String, toEnglish: Boolean): String? = null
}
