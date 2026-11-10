package com.ngerchev.tiho.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ngerchev.tiho.ChatViewModel
import com.ngerchev.tiho.R
import com.ngerchev.tiho.update.UpdateState
import com.ngerchev.tiho.update.Updater

/** Shown over any screen when a newer build exists. One tap opens its download in the browser. */
@Composable
fun UpdateDialog(vm: ChatViewModel) {
    val context = LocalContext.current
    val state = vm.updateState
    if (state !is UpdateState.Available) return

    AlertDialog(
        onDismissRequest = { vm.dismissUpdate() },
        title = { Text(stringResource(R.string.update_title)) },
        text = { Text(stringResource(R.string.update_text, state.info.versionName)) },
        confirmButton = {
            TextButton(
                onClick = {
                    Updater.openDownload(context)
                    vm.dismissUpdate()
                }
            ) {
                Text(stringResource(R.string.update_now))
            }
        },
        dismissButton = {
            TextButton(onClick = { vm.dismissUpdate() }) {
                Text(stringResource(R.string.update_later))
            }
        }
    )
}
