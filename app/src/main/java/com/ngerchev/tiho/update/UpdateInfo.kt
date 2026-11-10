package com.ngerchev.tiho.update

/** A newer build that is available for download. */
data class UpdateInfo(val versionCode: Long, val versionName: String)

sealed interface UpdateState {
    data object None : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
}
