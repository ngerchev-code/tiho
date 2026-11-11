package com.ngerchev.tiho.ai

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional, off by default: sends the recognized text to an AI service with the user's own
 * key and gets it back with proper punctuation. Works with a free Groq key (gsk_...) or a
 * paid OpenAI key (sk-...). The text of the message is then processed by that service.
 */
object AiPunctuation {

    const val DEFAULT_MODEL = "gpt-6-luna"

    private const val OPENAI_BASE = "https://api.openai.com/v1/"
    private const val GROQ_BASE = "https://api.groq.com/openai/v1/"

    // Groq changes its model list from time to time, so the app asks which models exist and
    // takes the first good one from this list (or any chat model as a last resort).
    private val GROQ_PREFERRED = listOf(
        "openai/gpt-oss-120b",
        "llama-3.3-70b-versatile",
        "openai/gpt-oss-20b",
        "llama-3.1-8b-instant"
    )
    private val NOT_FOR_CHAT = listOf("whisper", "guard", "tts", "playai", "orpheus", "distil")

    @Volatile
    private var groqModel: String? = null

    private const val INSTRUCTIONS =
        "You fix dictated text. Add correct punctuation and capitalisation. Keep every word " +
            "and the language exactly as given. Do not translate, add, remove or reorder words. " +
            "Reply with the corrected text only."

    private fun open(url: String, apiKey: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 30_000
        c.setRequestProperty("Authorization", "Bearer " + apiKey.trim())
        return c
    }

    /** Picks a chat model that this Groq account can use right now. */
    private fun pickGroqModel(apiKey: String): String? {
        groqModel?.let { return it }
        var c: HttpURLConnection? = null
        return try {
            val conn = open(GROQ_BASE + "models", apiKey)
            c = conn
            if (conn.responseCode != 200) return null
            val data = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                .getJSONArray("data")
            val ids = (0 until data.length()).map { data.getJSONObject(it).getString("id") }
            val chosen = GROQ_PREFERRED.firstOrNull { it in ids }
                ?: ids.firstOrNull { id -> NOT_FOR_CHAT.none { id.lowercase().contains(it) } }
            groqModel = chosen
            chosen
        } catch (_: Exception) {
            null
        } finally {
            c?.disconnect()
        }
    }

    private const val EDITOR =
        "You are a careful editor of Bulgarian and English. Correct spelling, grammar, " +
            "punctuation, style and word choice so the text reads naturally and correctly. " +
            "Keep the meaning, the language and the tone. Do not translate and do not add new " +
            "information. Reply with the corrected text only, without comments or quotes."

    /** Blocking. Punctuation only; null on any problem (the caller keeps the original). */
    fun fix(text: String, apiKey: String, model: String): String? =
        complete(text, apiKey, model, INSTRUCTIONS, 0.6f, 1.6f)

    /** Blocking. Full proofreading: spelling, grammar, punctuation, style and word choice. */
    fun proofread(text: String, apiKey: String, model: String): String? =
        complete(text, apiKey, model, EDITOR, 0.4f, 2.0f)

    private fun complete(
        text: String,
        apiKey: String,
        model: String,
        instructions: String,
        minRatio: Float,
        maxRatio: Float
    ): String? {
        val groq = Transcriber.isGroqKey(apiKey)
        val chosenModel = if (groq) {
            model.ifBlank { pickGroqModel(apiKey) ?: return null }
        } else {
            model.ifBlank { DEFAULT_MODEL }
        }
        var connection: HttpURLConnection? = null
        return try {
            val body = JSONObject()
                .put("model", chosenModel)
                .put("temperature", 0)
                .put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", instructions))
                        .put(JSONObject().put("role", "user").put("content", text))
                )
            val c = open((if (groq) GROQ_BASE else OPENAI_BASE) + "chat/completions", apiKey)
            connection = c
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (c.responseCode != 200) {
                // The remembered Groq model may have been retired: choose again next time.
                if (groq) groqModel = null
                return null
            }
            val response = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val result = response.getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content").trim()
            // Guard against an answer that is clearly not "the same text with punctuation".
            val ratio = result.length.toFloat() / text.length.coerceAtLeast(1)
            if (result.isEmpty() || ratio < minRatio || ratio > maxRatio) null else result.trim('"', '“', '”', '„')
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
