package com.ngerchev.tiho.ai

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional, off by default: sends the recognized text to the OpenAI API with the user's own
 * key and gets it back with proper punctuation. Only used when the user switches it on and
 * enters a key; the text of the message is then processed by OpenAI.
 */
object AiPunctuation {

    const val DEFAULT_MODEL = "gpt-6-luna"

    private const val ENDPOINT = "https://api.openai.com/v1/chat/completions"
    private const val INSTRUCTIONS =
        "You fix dictated text. Add correct punctuation and capitalisation. Keep every word " +
            "and the language exactly as given. Do not translate, add, remove or reorder words. " +
            "Reply with the corrected text only."

    /** Blocking. Returns the corrected text, or null on any problem (the caller keeps the original). */
    fun fix(text: String, apiKey: String, model: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val body = JSONObject()
                .put("model", model.ifBlank { DEFAULT_MODEL })
                .put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", INSTRUCTIONS))
                        .put(JSONObject().put("role", "user").put("content", text))
                )
            val c = URL(ENDPOINT).openConnection() as HttpURLConnection
            connection = c
            c.requestMethod = "POST"
            c.connectTimeout = 10_000
            c.readTimeout = 30_000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Authorization", "Bearer " + apiKey.trim())
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (c.responseCode != 200) return null
            val response = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val result = response.getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content").trim()
            // Guard against an answer that is clearly not "the same text with punctuation".
            val ratio = result.length.toFloat() / text.length.coerceAtLeast(1)
            if (result.isEmpty() || ratio < 0.6f || ratio > 1.6f) null else result
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
