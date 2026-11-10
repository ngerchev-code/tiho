package com.ngerchev.tiho.translate

import com.google.android.gms.tasks.Task
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Bulgarian <-> English translation with Google ML Kit. The translation itself runs on the
 * phone; the text is not sent anywhere. The language pack (about 30 MB per language) is
 * downloaded once, the first time translation is used.
 */
object Translator {
    const val ENABLED = true

    /** Returns the translated text, or null when it could not be translated. */
    suspend fun translate(text: String, toEnglish: Boolean): String? = try {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(if (toEnglish) TranslateLanguage.BULGARIAN else TranslateLanguage.ENGLISH)
            .setTargetLanguage(if (toEnglish) TranslateLanguage.ENGLISH else TranslateLanguage.BULGARIAN)
            .build()
        val client = Translation.getClient(options)
        try {
            client.downloadModelIfNeeded().await()
            client.translate(text).await()
        } finally {
            client.close()
        }
    } catch (_: Exception) {
        null
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result -> continuation.resume(result) }
        addOnFailureListener { error -> continuation.resumeWithException(error) }
    }
}
