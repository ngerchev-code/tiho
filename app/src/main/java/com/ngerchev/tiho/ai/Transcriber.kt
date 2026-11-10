package com.ngerchev.tiho.ai

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer

enum class TranscribeError { NO_AUDIO, TOO_LONG, BAD_KEY, LIMIT, FAILED }

class TranscribeResult(val text: String?, val error: TranscribeError?)

/**
 * Turns a recording the user picks (audio or video) into text with the OpenAI transcription
 * API and the user's own key. The phone's built-in recognizer only listens to the microphone,
 * so files need an online service.
 *
 * Privacy: the audio is sent to OpenAI for transcription. A temporary copy is made in the
 * app's cache and deleted as soon as the request finishes; nothing is kept on the phone.
 */
object Transcriber {

    private const val OPENAI_ENDPOINT = "https://api.openai.com/v1/audio/transcriptions"

    // Groq runs the open Whisper model and has a free plan (no card needed). Its keys start
    // with "gsk_"; the request format is the same as OpenAI's.
    private const val GROQ_ENDPOINT = "https://api.groq.com/openai/v1/audio/transcriptions"
    private val GROQ_MODELS = listOf("whisper-large-v3", "whisper-large-v3-turbo")

    fun isGroqKey(key: String): Boolean = key.trim().startsWith("gsk_")

    /** True when the text looks like a key of one of the two supported services. */
    fun looksLikeKey(key: String): Boolean {
        val k = key.trim()
        return k.length >= 20 && (k.startsWith("gsk_") || k.startsWith("sk-"))
    }
    private const val MAX_UPLOAD = 24L * 1024L * 1024L

    // The current model first; the older one as a fallback while it still exists.
    private val MODELS = listOf("gpt-transcribe", "whisper-1")

    private val DIRECT_EXTENSIONS = setOf(
        "mp3", "mp4", "mpeg", "mpga", "m4a", "wav", "webm", "ogg", "oga", "flac"
    )

    /** Blocking: call from a background thread. */
    fun transcribe(context: Context, uri: Uri, apiKey: String, languageTag: String): TranscribeResult {
        val dir = File(context.cacheDir, "transcribe")
        try {
            dir.deleteRecursively()
            dir.mkdirs()
            val resolver = context.contentResolver
            var name = "recording"
            var size = -1L
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (n >= 0) c.getString(n)?.let { name = it }
                        val s = c.getColumnIndex(OpenableColumns.SIZE)
                        if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
                    }
                }
            val mime = resolver.getType(uri) ?: ""
            val extension = name.substringAfterLast('.', "").lowercase()
            val isVideo = mime.startsWith("video/")
            val directOk = extension in DIRECT_EXTENSIONS && size in 0..MAX_UPLOAD

            // Video, large files and unusual formats: keep only the sound track (much smaller).
            var upload: File? = null
            if (isVideo || !directOk) {
                val audio = File(dir, "audio.m4a")
                if (extractAudio(context, uri, audio) && audio.length() > 0) upload = audio
            }
            if (upload == null) {
                if (!directOk) {
                    return TranscribeResult(
                        null,
                        if (extension in DIRECT_EXTENSIONS) TranscribeError.TOO_LONG else TranscribeError.NO_AUDIO
                    )
                }
                val copy = File(dir, "audio.$extension")
                val input = resolver.openInputStream(uri)
                    ?: return TranscribeResult(null, TranscribeError.FAILED)
                input.use { stream -> copy.outputStream().use { out -> stream.copyTo(out) } }
                upload = copy
            }
            if (upload.length() > MAX_UPLOAD) return TranscribeResult(null, TranscribeError.TOO_LONG)

            val language = if (languageTag.startsWith("bg")) "bg" else "en"
            var lastError = TranscribeError.FAILED
            val groq = isGroqKey(apiKey)
            val endpoint = if (groq) GROQ_ENDPOINT else OPENAI_ENDPOINT
            for (model in if (groq) GROQ_MODELS else MODELS) {
                val (code, body) = post(endpoint, upload, apiKey, model, language)
                when {
                    code == 200 -> {
                        val text = JSONObject(body).optString("text").trim()
                        return if (text.isEmpty()) {
                            TranscribeResult(null, TranscribeError.NO_AUDIO)
                        } else {
                            TranscribeResult(text, null)
                        }
                    }

                    code == 401 -> return TranscribeResult(null, TranscribeError.BAD_KEY)
                    code == 413 -> return TranscribeResult(null, TranscribeError.TOO_LONG)
                    code == 429 -> return TranscribeResult(null, TranscribeError.LIMIT)
                    else -> lastError = TranscribeError.FAILED // e.g. unknown model: try the next
                }
            }
            return TranscribeResult(null, lastError)
        } catch (_: Exception) {
            return TranscribeResult(null, TranscribeError.FAILED)
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Copies the AAC sound track of a file into a small .m4a, without re-encoding. */
    private fun extractAudio(context: Context, uri: Uri, out: File): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            extractor.setDataSource(context, uri, null)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    track = i
                    format = f
                    break
                }
            }
            val audioFormat = format ?: return false
            if (audioFormat.getString(MediaFormat.KEY_MIME) != "audio/mp4a-latm") return false
            extractor.selectTrack(track)
            val m = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = m
            val outTrack = m.addTrack(audioFormat)
            m.start()
            val buffer = ByteBuffer.allocate(1 shl 20)
            val info = MediaCodec.BufferInfo()
            var wrote = false
            while (true) {
                val read = extractor.readSampleData(buffer, 0)
                if (read < 0) break
                info.set(0, read, extractor.sampleTime, extractor.sampleFlags)
                m.writeSampleData(outTrack, buffer, info)
                wrote = true
                extractor.advance()
            }
            m.stop()
            wrote
        } catch (_: Exception) {
            false
        } finally {
            try {
                muxer?.release()
            } catch (_: Exception) {
            }
            extractor.release()
        }
    }

    private fun post(
        endpoint: String,
        file: File,
        apiKey: String,
        model: String,
        language: String
    ): Pair<Int, String> {
        val boundary = "----tiho" + System.nanoTime()
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 5 * 60_000
            connection.doOutput = true
            connection.setChunkedStreamingMode(64 * 1024)
            connection.setRequestProperty("Authorization", "Bearer " + apiKey.trim())
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.outputStream.use { out ->
                fun field(name: String, value: String) {
                    out.write(
                        ("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")
                            .toByteArray(Charsets.UTF_8)
                    )
                }
                field("model", model)
                field("language", language)
                out.write(
                    (
                        "--$boundary\r\nContent-Disposition: form-data; name=\"file\"; " +
                            "filename=\"${file.name}\"\r\nContent-Type: application/octet-stream\r\n\r\n"
                        ).toByteArray(Charsets.UTF_8)
                )
                file.inputStream().use { it.copyTo(out) }
                out.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return code to body
        } finally {
            connection.disconnect()
        }
    }
}
