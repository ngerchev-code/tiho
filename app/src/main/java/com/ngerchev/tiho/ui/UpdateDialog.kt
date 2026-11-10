package com.ngerchev.tiho.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ngerchev.tiho.ChatViewModel
import com.ngerchev.tiho.R
import com.ngerchev.tiho.update.UpdateState
import com.ngerchev.tiho.update.Updater

/** Shown over any screen when a newer build exists. One tap downloads it, one more installs. */
@Composable
fun UpdateDialog(vm: ChatViewModel) {
    val context = LocalContext.current
    val state = vm.updateState
    if (state is UpdateState.None) return

    var askedPermission by remember { mutableStateOf(false) }
    val tryInstall: (UpdateState.Ready) -> Unit = { ready ->
        if (Updater.canInstall(context)) {
            Updater.install(context, ready.file)
        } else {
            askedPermission = true
            Updater.openInstallPermission(context)
        }
    }

    // As soon as the download finishes, go straight to Android's install screen.
    if (state is UpdateState.Ready) {
        LaunchedEffect(state.file) {
            if (Updater.canInstall(context)) Updater.install(context, state.file)
        }
    }

    AlertDialog(
        onDismissRequest = { if (state !is UpdateState.Downloading) vm.dismissUpdate() },
        title = { Text(stringResource(R.string.update_title)) },
        text = {
            Column {
                when (state) {
                    is UpdateState.Available ->
                        Text(stringResource(R.string.update_text, state.info.versionName))

                    is UpdateState.Downloading -> {
                        Text(stringResource(R.string.update_downloading))
                        Spacer(Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    is UpdateState.Ready -> Text(
                        stringResource(
                            if (askedPermission || !Updater.canInstall(context)) {
                                R.string.update_permission_text
                            } else {
                                R.string.update_ready_text
                            }
                        )
                    )

                    is UpdateState.Failed -> Text(stringResource(R.string.update_failed))
                    is UpdateState.None -> Unit
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateState.Available -> TextButton(onClick = { vm.startUpdate() }) {
                    Text(stringResource(R.string.update_now))
                }

                is UpdateState.Ready -> TextButton(onClick = { tryInstall(state) }) {
                    Text(stringResource(R.string.update_install))
                }

                is UpdateState.Failed -> TextButton(onClick = { vm.startUpdate() }) {
                    Text(stringResource(R.string.update_retry))
                }

                else -> Unit
            }
        },
        dismissButton = {
            if (state !is UpdateState.Downloading) {
                TextButton(onClick = { vm.dismissUpdate() }) {
                    Text(stringResource(R.string.update_later))
                }
            }
        }
    )
}
