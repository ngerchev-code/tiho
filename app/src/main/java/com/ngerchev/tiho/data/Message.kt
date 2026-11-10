package com.ngerchev.tiho.data

/** The file types the app accepts. */
enum class FileKind(val label: String) {
    IMAGE("IMG"), WORD("DOC"), EXCEL("XLS"), POWERPOINT("PPT"), PDF("PDF");

    companion object {
        private val BY_EXTENSION = mapOf(
            "jpg" to IMAGE, "jpeg" to IMAGE, "png" to IMAGE,
            "doc" to WORD, "docx" to WORD,
            "xls" to EXCEL, "xlsx" to EXCEL,
            "ppt" to POWERPOINT, "pptx" to POWERPOINT,
            "pdf" to PDF
        )

        private val BY_MIME = mapOf(
            "image/jpeg" to IMAGE,
            "image/png" to IMAGE,
            "application/msword" to WORD,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to WORD,
            "application/vnd.ms-excel" to EXCEL,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to EXCEL,
            "application/vnd.ms-powerpoint" to POWERPOINT,
            "application/vnd.openxmlformats-officedocument.presentationml.presentation" to POWERPOINT,
            "application/pdf" to PDF
        )

        /** MIME types offered in the system file picker. */
        val PICKER_MIME_TYPES: Array<String> = BY_MIME.keys.toTypedArray()

        private val DEFAULT_EXTENSION = mapOf(
            "image/jpeg" to "jpg",
            "image/png" to "png",
            "application/msword" to "doc",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to "docx",
            "application/vnd.ms-excel" to "xls",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to "xlsx",
            "application/vnd.ms-powerpoint" to "ppt",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation" to "pptx",
            "application/pdf" to "pdf"
        )

        private val MIME_BY_EXTENSION = mapOf(
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "png" to "image/png",
            "doc" to "application/msword",
            "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xls" to "application/vnd.ms-excel",
            "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "ppt" to "application/vnd.ms-powerpoint",
            "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "pdf" to "application/pdf"
        )

        fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase()

        /** Returns null when the file is not one of the supported types. */
        fun of(name: String, mime: String): FileKind? =
            BY_EXTENSION[extensionOf(name)] ?: BY_MIME[mime.lowercase()]

        fun defaultExtension(mime: String): String? = DEFAULT_EXTENSION[mime.lowercase()]

        fun mimeFor(name: String, reported: String): String =
            MIME_BY_EXTENSION[extensionOf(name)] ?: reported.ifBlank { "application/octet-stream" }
    }
}

/** A file attached to a message. The file itself is a private copy inside the app. */
data class Attachment(
    val name: String,
    val mime: String,
    val path: String,
    val size: Long
) {
    val kind: FileKind get() = FileKind.of(name, mime) ?: FileKind.PDF
}

/** One sent message: text and/or files. There is no audio anywhere in the app. */
data class Message(
    val id: Long,
    val text: String,
    val timestamp: Long,
    val attachments: List<Attachment> = emptyList()
)
