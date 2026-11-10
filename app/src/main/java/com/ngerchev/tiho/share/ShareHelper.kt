package com.ngerchev.tiho.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * A place text can be handed to. Today the only implementation is [ChatGptShareTarget], which
 * uses Android sharing and needs no API key. A future optional OpenAI API integration would be
 * a second implementation of this interface; nothing else in the app would have to change.
 */
interface TextTarget {
    fun send(context: Context, text: String): ShareResult
}

enum class ShareResult { OPENED_APP, OPENED_WEB, FAILED }

object ChatGptShareTarget : TextTarget {

    private const val CHATGPT_PACKAGE = "com.openai.chatgpt"
    private const val MAX_URL_TEXT = 1500

    override fun send(context: Context, text: String): ShareResult {
        // Always copy first, so the text can simply be pasted whatever happens next.
        ShareHelper.copy(context, text)

        // 1) Hand the text straight to the ChatGPT app.
        val toApp = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            setPackage(CHATGPT_PACKAGE)
        }
        try {
            context.startActivity(toApp)
            return ShareResult.OPENED_APP
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }

        // 2) No app: open chatgpt.com in the browser, with the text pre-filled when short enough.
        val url = if (text.length <= MAX_URL_TEXT) {
            "https://chatgpt.com/?q=" + Uri.encode(text)
        } else {
            "https://chatgpt.com/"
        }
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            ShareResult.OPENED_WEB
        } catch (_: Exception) {
            ShareResult.FAILED
        }
    }
}

object ShareHelper {

    fun copy(context: Context, text: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Tiho", text))
        } catch (_: Exception) {
        }
    }

    /** Standard Android share sheet: the user picks any app. */
    fun shareAnywhere(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        try {
            context.startActivity(Intent.createChooser(send, null))
        } catch (_: Exception) {
        }
    }
}
