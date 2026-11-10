package com.ngerchev.tiho.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Update notice for the edition installed from the download link.
 *
 * The app reads one small public file from its own GitHub repository to learn the number of
 * the latest build. When that build is newer, it offers to open the download page in the
 * browser; the user downloads and installs it there, exactly like the first time. The app
 * itself never downloads or installs anything, and nothing about the user is sent.
 */
object Updater {
    const val ENABLED = true

    private const val BASE = "https://github.com/ngerchev-code/tiho/releases/latest/download/"

    /** Blocking. Returns the newer build, or null when up to date. Throws when offline. */
    fun check(context: Context): UpdateInfo? {
        val connection = URL(BASE + "version.json").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Tiho-Android")
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

    /** Opens the download link of the newest version in the browser. */
    fun openDownload(context: Context): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BASE + "Tiho.apk")))
        true
    } catch (_: Exception) {
        false
    }
}
