package com.ngerchev.tiho.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Holds the conversation. Messages always live in memory; when persistence is on they are
 * also written to one small private JSON file so they survive closing the app.
 * Anything older than [RETENTION_MS] is removed.
 */
class MessageStore(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)
    private val io = Executors.newSingleThreadExecutor()
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private var persist = true
    private var lastId = 0L

    /** Call once at start. Loads what is still fresh and drops everything expired. */
    fun init(persistEnabled: Boolean) {
        persist = persistEnabled
        if (persist) {
            val cutoff = System.currentTimeMillis() - RETENTION_MS
            val loaded = readFile().filter { it.timestamp >= cutoff }
            _messages.value = loaded
            lastId = loaded.maxOfOrNull { it.id } ?: 0L
            save()
        } else {
            deleteFile()
        }
    }

    fun add(text: String): Message {
        val now = System.currentTimeMillis()
        lastId = maxOf(now, lastId + 1)
        val message = Message(lastId, text, now)
        _messages.value = _messages.value + message
        save()
        return message
    }

    fun delete(id: Long) {
        _messages.value = _messages.value.filterNot { it.id == id }
        save()
    }

    fun clear() {
        _messages.value = emptyList()
        save()
    }

    /** Removes messages older than 24 hours. Cheap, so it is called often. */
    fun purgeExpired(now: Long = System.currentTimeMillis()) {
        val cutoff = now - RETENTION_MS
        val current = _messages.value
        if (current.any { it.timestamp < cutoff }) {
            _messages.value = current.filter { it.timestamp >= cutoff }
            save()
        }
    }

    fun setPersistence(enabled: Boolean) {
        persist = enabled
        if (enabled) save() else deleteFile()
    }

    private fun save() {
        if (!persist) return
        val snapshot = _messages.value
        io.execute {
            try {
                if (snapshot.isEmpty()) {
                    file.delete()
                } else {
                    val array = JSONArray()
                    for (m in snapshot) {
                        array.put(
                            JSONObject()
                                .put("id", m.id)
                                .put("text", m.text)
                                .put("ts", m.timestamp)
                        )
                    }
                    val tmp = File(file.parentFile, "$FILE_NAME.tmp")
                    tmp.writeText(array.toString())
                    if (!tmp.renameTo(file)) {
                        file.writeText(array.toString())
                        tmp.delete()
                    }
                }
            } catch (_: Exception) {
                // Storage problems must never crash dictation; the in-memory list still works.
            }
        }
    }

    private fun deleteFile() {
        io.execute {
            try {
                file.delete()
            } catch (_: Exception) {
            }
        }
    }

    private fun readFile(): List<Message> {
        return try {
            if (!file.exists()) return emptyList()
            val array = JSONArray(file.readText())
            val out = ArrayList<Message>(array.length())
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                out.add(Message(o.getLong("id"), o.getString("text"), o.getLong("ts")))
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        const val RETENTION_MS = 24L * 60L * 60L * 1000L
        private const val FILE_NAME = "messages.json"
    }
}
