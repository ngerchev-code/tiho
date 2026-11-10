package com.ngerchev.tiho.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

enum class ImportError { UNSUPPORTED, TOO_LARGE, FAILED }

class ImportResult(val attachment: Attachment?, val error: ImportError?)

/**
 * Copies files the user picks into the app's private folder, so they can be shown in the chat
 * and handed to another app later. Each file gets its own sub-folder to keep its original name.
 */
class AttachmentStore(private val context: Context) {

    private val root = File(context.filesDir, DIR)

    /** Blocking: call from a background thread. */
    fun importFile(uri: Uri): ImportResult {
        var folder: File? = null
        try {
            val resolver = context.contentResolver
            var name = "file"
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val index = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) c.getString(index)?.let { name = it }
                }
            }
            val reportedMime = resolver.getType(uri) ?: ""
            FileKind.of(name, reportedMime) ?: return ImportResult(null, ImportError.UNSUPPORTED)

            var safeName = name.replace(UNSAFE_CHARS, "_").trim().take(120).ifBlank { "file" }
            if (FileKind.of(safeName, "") == null) {
                // The name has no usable extension: add one from the MIME type.
                val ext = FileKind.defaultExtension(reportedMime)
                if (ext != null) safeName = "$safeName.$ext"
            }

            val dir = File(root, System.nanoTime().toString())
            folder = dir
            if (!dir.mkdirs()) return ImportResult(null, ImportError.FAILED)
            val target = File(dir, safeName)

            var total = 0L
            val input = resolver.openInputStream(uri)
                ?: return fail(dir, ImportError.FAILED)
            input.use { stream ->
                target.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BYTES) return fail(dir, ImportError.TOO_LARGE)
                        out.write(buffer, 0, read)
                    }
                }
            }
            return ImportResult(
                Attachment(
                    name = safeName,
                    mime = FileKind.mimeFor(safeName, reportedMime),
                    path = target.absolutePath,
                    size = total
                ),
                null
            )
        } catch (_: Exception) {
            folder?.deleteRecursively()
            return ImportResult(null, ImportError.FAILED)
        }
    }

    private fun fail(dir: File, error: ImportError): ImportResult {
        dir.deleteRecursively()
        return ImportResult(null, error)
    }

    fun delete(attachment: Attachment) {
        deleteFolderOf(root, attachment)
    }

    companion object {
        const val DIR = "attachments"
        const val MAX_BYTES = 30L * 1024L * 1024L
        private val UNSAFE_CHARS = Regex("""[\\/:*?"<>|\p{Cntrl}]""")

        /** Deletes the private copy of a file (its whole sub-folder), never anything outside [root]. */
        fun deleteFolderOf(root: File, attachment: Attachment) {
            try {
                val folder = File(attachment.path).parentFile ?: return
                if (folder.parentFile?.absolutePath == root.absolutePath) folder.deleteRecursively()
            } catch (_: Exception) {
            }
        }
    }
}
