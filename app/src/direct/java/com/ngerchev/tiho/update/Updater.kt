package com.ngerchev.tiho.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Self-update for the edition installed from the download link.
 *
 * The app looks at the latest GitHub release of its own repository, and when that build is
 * newer it downloads the APK and hands it to Android's installer. Android only accepts the
 * update when it is signed with the same key as the installed app. Nothing about the user or
 * their messages is sent: these are two plain downloads of public files.
 */
object Updater {
    const val ENABLED = true

    private const val BASE = "https://github.com/ngerchev-code/tiho/releases/latest/download/"
    private const val APK_MIME = "application/vnd.android.package-archive"

    private fun open(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Tiho-Android")
        return connection
    }

    /** Blocking. Returns the newer build, or null when up to date. Throws when offline. */
    fun check(context: Context): UpdateInfo? {
        val connection = open(BASE + "version.json")
        try {
            if (connection.responseCode != 200) return null
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val latest = json.getLong("versionCode")
            val installed = PackageInfoCompat.getLongVersionCode(
                context.packageManager.getPackageInfo(context.packageName, 0)
            )
            return if (latest > installed) {
                UpdateInfo(latest, json.optString("versionName", latest.toString()))
            } else {
                null
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Blocking. Downloads the newest APK into the app's cache; null on any failure. */
    fun download(context: Context, onProgress: (Float) -> Unit): File? {
        val dir = File(context.cacheDir, "updates")
        val target = File(dir, "Tiho.apk")
        val partial = File(dir, "Tiho.apk.part")
        var connection: HttpURLConnection? = null
        return try {
            dir.mkdirs()
            target.delete()
            partial.delete()
            val c = open(BASE + "Tiho.apk")
            connection = c
            if (c.responseCode != 200) return null
            val total = c.contentLengthLong
            var done = 0L
            c.inputStream.use { input ->
                partial.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        done += read
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            if (done == 0L || (total > 0 && done != total)) return null
            if (partial.renameTo(target)) target else null
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    /** Android asks the user once to allow this app to install updates. */
    fun canInstall(context: Context): Boolean = try {
        context.packageManager.canRequestPackageInstalls()
    } catch (_: Exception) {
        false
    }

    fun openInstallPermission(context: Context) {
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.packageName)
                )
            )
        } catch (_: Exception) {
        }
    }

    /** Opens Android's own install screen for the downloaded file. */
    fun install(context: Context, file: File): Boolean = try {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}
