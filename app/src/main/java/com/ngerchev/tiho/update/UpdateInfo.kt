package com.ngerchev.tiho.update

import java.io.File

/** A newer build that is available for download. */
data class UpdateInfo(val versionCode: Long, val versionName: String)

sealed interface UpdateState {
    data object None : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data class Ready(val file: File) : UpdateState
    data object Failed : UpdateState
}
