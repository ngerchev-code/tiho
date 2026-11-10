package com.ngerchev.tiho.update

import android.content.Context

/**
 * Google Play edition: updates come from Play. This edition has no internet permission at all.
 */
object Updater {
    const val ENABLED = false

    @Suppress("UNUSED_PARAMETER")
    fun check(context: Context): UpdateInfo? = null

    @Suppress("UNUSED_PARAMETER")
    fun openDownload(context: Context): Boolean = false
}
