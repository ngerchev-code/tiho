package com.ngerchev.tiho.update

import android.content.Context
import java.io.File

/**
 * Google Play edition: the app never updates itself (Play does it, and Play policy forbids
 * anything else). This edition has no internet permission at all.
 */
object Updater {
    const val ENABLED = false

    @Suppress("UNUSED_PARAMETER")
    fun check(context: Context): UpdateInfo? = null

    @Suppress("UNUSED_PARAMETER")
    fun download(context: Context, onProgress: (Float) -> Unit): File? = null

    @Suppress("UNUSED_PARAMETER")
    fun canInstall(context: Context): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun openInstallPermission(context: Context) = Unit

    @Suppress("UNUSED_PARAMETER")
    fun install(context: Context, file: File): Boolean = false
}
