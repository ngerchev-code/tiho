package com.ngerchev.tiho.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ngerchev.tiho.ChatViewModel
import com.ngerchev.tiho.R
import com.ngerchev.tiho.Screen
import com.ngerchev.tiho.data.Message
import com.ngerchev.tiho.data.SettingsStore
import com.ngerchev.tiho.share.ChatGptShareTarget
import com.ngerchev.tiho.share.ShareHelper
import com.ngerchev.tiho.share.ShareResult
import com.ngerchev.tiho.speech.DictationState
import com.ngerchev.tiho.speech.SpeechEngine
import com.ngerchev.tiho.speech.SpeechNotice
import com.ngerchev.tiho.speech.SpeechUiState
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: ChatViewModel) {
    val context = LocalContext.current
    val messages by vm.messages.collectAsStateWithLifecycle()
    val speech by vm.speechState.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    val micActive = speech.state == DictationState.LISTENING ||
        speech.state == DictationState.STARTING

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.startDictation() else vm.onPermissionDenied()
    }

    LaunchedEffect(Unit) {
        vm.events.collect { resId -> snackbar.showSnackbar(context.getString(resId)) }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    val onMic: () -> Unit = {
        if (micActive) {
            vm.pauseDictation()
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            vm.startDictation()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val copyText: (String) -> Unit = { text ->
        ShareHelper.copy(context, text)
        // Android 13+ shows its own "copied" confirmation.
        if (Build.VERSION.SDK_INT < 33) vm.notify(R.string.msg_copied)
    }
    val toChatGpt: (String) -> Unit = { text ->
        when (ChatGptShareTarget.send(context, text)) {
            ShareResult.OPENED_APP -> Unit
            ShareResult.OPENED_WEB -> vm.notify(R.string.msg_chatgpt_web)
            ShareResult.FAILED -> vm.notify(R.string.msg_chatgpt_failed)
        }
    }
    val withConversation: ((String) -> Unit) -> Unit = { action ->
        val all = vm.conversationText()
        if (all.isBlank()) vm.notify(R.string.msg_nothing_to_share) else action(all)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    TextButton(onClick = { vm.toggleLanguage() }) {
                        Text(
                            text = if (settings.languageTag == SettingsStore.LANG_BG) "BG" else "EN",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                painterResource(R.drawable.ic_more),
                                contentDescription = stringResource(R.string.cd_menu)
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_copy_all)) },
                                onClick = { menuOpen = false; withConversation(copyText) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_chatgpt_all)) },
                                onClick = { menuOpen = false; withConversation(toChatGpt) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_share_all)) },
                                onClick = {
                                    menuOpen = false
                                    withConversation { ShareHelper.shareAnywhere(context, it) }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_clear)) },
                                onClick = { menuOpen = false; confirmClear = true }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_language)) },
                                onClick = { menuOpen = false; vm.navigate(Screen.LANGUAGE) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_settings)) },
                                onClick = { menuOpen = false; vm.navigate(Screen.SETTINGS) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_about)) },
                                onClick = { menuOpen = false; vm.navigate(Screen.ABOUT) }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Composer(
                speech = speech,
                languageTag = settings.languageTag,
                micActive = micActive,
                draft = vm.draft,
                onDraftChange = vm::onDraftChange,
                onMic = onMic,
                onStop = { vm.stopDictation() },
                onClearDraft = { vm.clearDraft() },
                onSend = { vm.send() },
                onPunctuation = { vm.insertPunctuation(it) },
                onDismissNotice = { vm.dismissNotice() },
                onOpenAppSettings = { openAppSettings(context) }
            )
        }
    ) { padding ->
        if (messages.isEmpty()) {
            EmptyState(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        onCopy = { copyText(message.text) },
                        onChatGpt = { toChatGpt(message.text) },
                        onDelete = { vm.deleteMessage(message.id) }
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_title)) },
            text = { Text(stringResource(R.string.clear_text)) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; vm.clearConversation() }) {
                    Text(stringResource(R.string.action_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painterResource(R.drawable.ic_mic),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    onCopy: () -> Unit,
    onChatGpt: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val time = remember(message.timestamp) {
        DateFormat.getTimeFormat(context).format(Date(message.timestamp))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 40.dp),
        horizontalAlignment = Alignment.End
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 20.dp, topEnd = 20.dp, bottomEnd = 6.dp, bottomStart = 20.dp
            ),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            SelectionContainer {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            SmallAction(R.drawable.ic_copy, stringResource(R.string.cd_copy), onCopy)
            SmallAction(R.drawable.ic_share, stringResource(R.string.cd_share_chatgpt), onChatGpt)
            SmallAction(R.drawable.ic_close, stringResource(R.string.cd_delete), onDelete)
        }
    }
}

@Composable
private fun SmallAction(iconRes: Int, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(
            painterResource(iconRes),
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** Bottom panel: status, live words, editable draft, punctuation keys and the big microphone. */
@Composable
private fun Composer(
    speech: SpeechUiState,
    languageTag: String,
    micActive: Boolean,
    draft: TextFieldValue,
    onDraftChange: (TextFieldValue) -> Unit,
    onMic: () -> Unit,
    onStop: () -> Unit,
    onClearDraft: () -> Unit,
    onSend: () -> Unit,
    onPunctuation: (String) -> Unit,
    onDismissNotice: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                )
        ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                speech.notice?.let { notice ->
                    NoticeBanner(notice, onDismissNotice, onOpenAppSettings)
                }

                StatusRow(speech, languageTag)

                if (speech.partial.isNotBlank()) {
                    Text(
                        text = speech.partial,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.draft_hint)) },
                    shape = RoundedCornerShape(20.dp),
                    maxLines = 5
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(".", ",", "?", "!", "\n").forEach { mark ->
                        Surface(
                            onClick = { onPunctuation(mark) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (mark == "\n") "↵" else mark,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val sessionOpen = speech.state != DictationState.IDLE
                    FilledTonalIconButton(
                        onClick = { if (sessionOpen) onStop() else onClearDraft() },
                        enabled = sessionOpen || draft.text.isNotEmpty(),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            painterResource(
                                if (sessionOpen) R.drawable.ic_stop else R.drawable.ic_close
                            ),
                            contentDescription = stringResource(
                                if (sessionOpen) R.string.cd_stop else R.string.cd_clear_draft
                            )
                        )
                    }

                    MicButton(active = micActive, onClick = onMic)

                    FilledIconButton(
                        onClick = onSend,
                        enabled = draft.text.isNotBlank(),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_send),
                            contentDescription = stringResource(R.string.cd_send)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(speech: SpeechUiState, languageTag: String) {
    val listening = speech.state == DictationState.LISTENING
    val languageName = stringResource(
        if (languageTag == SettingsStore.LANG_BG) R.string.lang_bg else R.string.lang_en
    )
    val status = stringResource(
        when (speech.state) {
            DictationState.IDLE -> R.string.status_ready
            DictationState.STARTING -> R.string.status_starting
            DictationState.LISTENING -> R.string.status_listening
            DictationState.PAUSED -> R.string.status_paused
        }
    )
    val engine = when (speech.engine) {
        SpeechEngine.ON_DEVICE -> stringResource(R.string.engine_on_device)
        SpeechEngine.OFFLINE_PREFERRED, SpeechEngine.ONLINE -> stringResource(R.string.engine_online)
        null -> null
    }
    val detail = if (listening && engine != null) "$languageName · $engine" else languageName

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .background(
                    if (listening) recordColor() else MaterialTheme.colorScheme.outline,
                    CircleShape
                )
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = status,
            style = MaterialTheme.typography.labelLarge,
            color = if (listening) recordColor() else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun NoticeBanner(
    notice: SpeechNotice,
    onDismiss: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    val text = stringResource(
        when (notice) {
            SpeechNotice.NO_PERMISSION -> R.string.notice_no_permission
            SpeechNotice.UNAVAILABLE -> R.string.notice_unavailable
            SpeechNotice.NETWORK -> R.string.notice_network
            SpeechNotice.AUDIO -> R.string.notice_audio
            SpeechNotice.BUSY -> R.string.notice_busy
            SpeechNotice.LANGUAGE -> R.string.notice_language
            SpeechNotice.SILENCE -> R.string.notice_silence
            SpeechNotice.PHONE_CALL -> R.string.notice_phone_call
            SpeechNotice.FELL_BACK_ONLINE -> R.string.notice_fell_back
            SpeechNotice.GENERIC -> R.string.notice_generic
        }
    )
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
                if (notice == SpeechNotice.NO_PERMISSION) {
                    TextButton(
                        onClick = onOpenAppSettings,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(stringResource(R.string.action_open_settings))
                    }
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.cd_dismiss),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun MicButton(active: Boolean, onClick: () -> Unit) {
    val record = recordColor()
    Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
        if (active) {
            // The one animation in the app: a soft pulse while the microphone is live.
            val transition = rememberInfiniteTransition(label = "pulse")
            val pulse by transition.animateFloat(
                initialValue = 1f,
                targetValue = 1.28f,
                animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                label = "pulseScale"
            )
            Box(
                Modifier
                    .size(76.dp)
                    .scale(pulse)
                    .alpha(0.18f)
                    .background(record, CircleShape)
            )
        }
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = if (active) record else MaterialTheme.colorScheme.primary,
            contentColor = if (active) Color.White else MaterialTheme.colorScheme.onPrimary,
            shadowElevation = 4.dp,
            modifier = Modifier.size(76.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(if (active) R.drawable.ic_pause else R.drawable.ic_mic),
                    contentDescription = stringResource(
                        if (active) R.string.cd_mic_pause else R.string.cd_mic_start
                    ),
                    modifier = Modifier.size(34.dp)
                )
            }
        }
    }
}

private fun openAppSettings(context: Context) {
    try {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )
        )
    } catch (_: Exception) {
    }
}
