package com.ngerchev.tiho.data

/** One sent message. Only text is ever kept; there is no audio anywhere in the app. */
data class Message(
    val id: Long,
    val text: String,
    val timestamp: Long
)
