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
 * Holds the conversation. Messages always live in memory; when the chosen retention persists,
 * they are also written to one small private JSON file so they survive closing the app.
 * Anything older than the retention period is removed, together with its attached files.
 */
class MessageStore(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)
    private val attachmentsRoot = File(context.filesDir, AttachmentStore.DIR)
    private val io = Executors.newSingleThreadExecutor()
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private var retention = Retention.HOURS_24
    private var lastId = 0L

    /** Call once at start. Loads what is still fresh and drops everything expired. */
    fun init(initial: Retention) {
        retention = initial
        if (retention.persist) {
            val cutoff = System.currentTimeMillis() - retention.millis
            val loaded = readFile().filter { it.timestamp >= cutoff }
            _messages.value = loaded
            lastId = loaded.maxOfOrNull { it.id } ?: 0L
            save()
        } else {
            deleteFile()
        }
        removeOrphanFiles(_messages.value)
    }

    fun add(text: String, attachments: List<Attachment>): Message {
        val now = System.currentTimeMillis()
        lastId = maxOf(now, lastId + 1)
        val message = Message(lastId, text, now, attachments)
        _messages.value = _messages.value + message
        save()
        return message
    }

    fun delete(id: Long) {
        val removed = _messages.value.filter { it.id == id }
        _messages.value = _messages.value.filterNot { it.id == id }
        deleteFiles(removed)
        save()
    }

    /** Removes a message but keeps its files, so it can go back into the draft for editing. */
    fun take(id: Long): Message? {
        val message = _messages.value.firstOrNull { it.id == id } ?: return null
        _messages.value = _messages.value.filterNot { it.id == id }
        save()
        return message
    }

    fun clear() {
        val removed = _messages.value
        _messages.value = emptyList()
        deleteFiles(removed)
        save()
    }

    /** Removes messages older than the retention period. Cheap, so it is called often. */
    fun purgeExpired(now: Long = System.currentTimeMillis()) {
        val cutoff = now - retention.millis
        val current = _messages.value
        val expired = current.filter { it.timestamp < cutoff }
        if (expired.isNotEmpty()) {
            _messages.value = current.filter { it.timestamp >= cutoff }
            deleteFiles(expired)
            save()
        }
    }

    fun setRetention(value: Retention) {
        retention = value
        purgeExpired()
        if (value.persist) save() else deleteFile()
    }

    private fun deleteFiles(messages: List<Message>) {
        val attachments = messages.flatMap { it.attachments }
        if (attachments.isEmpty()) return
        io.execute {
            for (a in attachments) AttachmentStore.deleteFolderOf(attachmentsRoot, a)
        }
    }

    /** Files left behind by an unsent draft or a closed session are removed at start. */
    private fun removeOrphanFiles(kept: List<Message>) {
        val used = kept.flatMap { it.attachments }
            .mapNotNull { File(it.path).parentFile?.name }
            .toSet()
        io.execute {
            try {
                attachmentsRoot.listFiles()?.forEach { folder ->
                    if (folder.name !in used) folder.deleteRecursively()
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun save() {
        if (!retention.persist) return
        val snapshot = _messages.value
        io.execute {
            try {
                if (snapshot.isEmpty()) {
                    file.delete()
                } else {
                    val array = JSONArray()
                    for (m in snapshot) {
                        val files = JSONArray()
                        for (a in m.attachments) {
                            files.put(
                                JSONObject()
                                    .put("n", a.name)
                                    .put("m", a.mime)
                                    .put("p", a.path)
                                    .put("s", a.size)
                            )
                        }
                        array.put(
                            JSONObject()
                                .put("id", m.id)
                                .put("text", m.text)
                                .put("ts", m.timestamp)
                                .put("files", files)
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
                val attachments = ArrayList<Attachment>()
                val files = o.optJSONArray("files")
                if (files != null) {
                    for (j in 0 until files.length()) {
                        val f = files.getJSONObject(j)
                        val attachment = Attachment(
                            name = f.getString("n"),
                            mime = f.getString("m"),
                            path = f.getString("p"),
                            size = f.optLong("s", 0L)
                        )
                        if (File(attachment.path).exists()) attachments.add(attachment)
                    }
                }
                out.add(Message(o.getLong("id"), o.getString("text"), o.getLong("ts"), attachments))
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val FILE_NAME = "messages.json"
    }
}
