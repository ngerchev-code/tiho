package com.ngerchev.tiho.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.ngerchev.tiho.data.Attachment
import com.ngerchev.tiho.data.SendTarget
import java.io.File

enum class ShareResult { OPENED_APP, OPENED_WEB, OPENED_MENU_FALLBACK, FAILED }

/**
 * Everything that leaves the app goes through here, and always by the user's own action:
 * the text and files are handed to another app with standard Android sharing. The app itself
 * has no network access.
 *
 * A future optional OpenAI API integration would be one more branch in [send]; the rest of
 * the app only knows about [SendTarget].
 */
object ShareHelper {

    private const val MAX_URL_TEXT = 1500

    fun copy(context: Context, text: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Tiho", text))
        } catch (_: Exception) {
        }
    }

    private fun uriFor(context: Context, attachment: Attachment): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".files", File(attachment.path))

    private fun buildIntent(context: Context, text: String, attachments: List<Attachment>): Intent {
        val existing = attachments.filter { File(it.path).exists() }
        if (existing.isEmpty()) {
            return Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
        }
        val uris = ArrayList<Uri>(existing.map { uriFor(context, it) })
        val mimes = existing.map { it.mime }.distinct()
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mimes.first()
                putExtra(Intent.EXTRA_STREAM, uris[0])
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = when {
                    mimes.size == 1 -> mimes.first()
                    mimes.all { it.startsWith("image/") } -> "image/*"
                    else -> "*/*"
                }
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }
        if (text.isNotBlank()) intent.putExtra(Intent.EXTRA_TEXT, text)
        // The receiving app gets temporary read access to exactly these files.
        val clip = ClipData.newRawUri("", uris[0])
        for (i in 1 until uris.size) clip.addItem(ClipData.Item(uris[i]))
        intent.clipData = clip
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return intent
    }

    private fun openMenu(context: Context, intent: Intent): Boolean = try {
        context.startActivity(Intent.createChooser(intent, null))
        true
    } catch (_: Exception) {
        false
    }

    fun send(
        context: Context,
        target: SendTarget,
        text: String,
        attachments: List<Attachment>
    ): ShareResult {
        val intent = try {
            buildIntent(context, text, attachments)
        } catch (_: Exception) {
            return ShareResult.FAILED
        }

        val packageName = target.packageName
        if (packageName == null) {
            return if (openMenu(context, intent)) ShareResult.OPENED_APP else ShareResult.FAILED
        }

        // ChatGPT: also copy the text, so it can simply be pasted whatever happens next.
        if (target == SendTarget.CHATGPT && text.isNotBlank()) copy(context, text)

        intent.setPackage(packageName)
        try {
            context.startActivity(intent)
            return ShareResult.OPENED_APP
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }
        intent.setPackage(null)

        // ChatGPT is not installed: open chatgpt.com, with the text pre-filled when short enough.
        if (target == SendTarget.CHATGPT && attachments.isEmpty()) {
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

        // The chosen app is not installed: let the user pick another one.
        return if (openMenu(context, intent)) ShareResult.OPENED_MENU_FALLBACK else ShareResult.FAILED
    }

    /** Opens an attached file in whatever app the phone has for it. */
    fun open(context: Context, attachment: Attachment): Boolean = try {
        val uri = uriFor(context, attachment)
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, attachment.mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(view)
        true
    } catch (_: Exception) {
        false
    }
}
