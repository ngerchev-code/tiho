package com.ngerchev.tiho.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ngerchev.tiho.ChatViewModel
import com.ngerchev.tiho.R
import com.ngerchev.tiho.Screen
import com.ngerchev.tiho.data.Attachment
import com.ngerchev.tiho.data.FileKind
import com.ngerchev.tiho.data.Message
import com.ngerchev.tiho.data.SendTarget
import com.ngerchev.tiho.data.SettingsStore
import com.ngerchev.tiho.share.ShareHelper
import com.ngerchev.tiho.share.ShareResult
import com.ngerchev.tiho.speech.DictationState
import com.ngerchev.tiho.speech.SpeechEngine
import com.ngerchev.tiho.speech.SpeechNotice
import com.ngerchev.tiho.speech.SpeechUiState
import com.ngerchev.tiho.translate.Translator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: ChatViewModel) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val messages by vm.messages.collectAsStateWithLifecycle()
    val speech by vm.speechState.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    // Messages ticked to be sent together. "selecting" switches the screen into that mode.
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var pendingPhoto by remember { mutableStateOf<File?>(null) }

    val micActive = speech.state == DictationState.LISTENING ||
        speech.state == DictationState.STARTING

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.startDictation() else vm.onPermissionDenied()
    }
    // The system file picker: no storage permission is needed, the user picks the files.
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) vm.addAttachments(uris)
    }

    // The phone's camera app takes the photo; this app needs no camera permission.
    val camera = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { saved ->
        pendingPhoto?.let { vm.onPhotoTaken(it, saved) }
        pendingPhoto = null
    }
    val takePhoto: () -> Unit = {
        try {
            val file = vm.newPhotoFile()
            pendingPhoto = file
            camera.launch(ShareHelper.uriForFile(context, file))
        } catch (_: Exception) {
            vm.notify(R.string.msg_no_camera)
        }
    }
    val leaveSelection: () -> Unit = {
        selecting = false
        selected = emptySet()
    }
    BackHandler(enabled = selecting) { leaveSelection() }

    LaunchedEffect(Unit) {
        vm.events.collect { resId -> snackbar.showSnackbar(context.getString(resId)) }
    }
    LaunchedEffect(messages.size) {
        // Larger than the real item count on purpose: the list clamps it to the last item.
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size * 2)
    }
    DisposableEffect(micActive, settings.keepScreenOn) {
        view.keepScreenOn = micActive && settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    val buzz: () -> Unit = {
        if (settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val onMic: () -> Unit = {
        buzz()
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
        if (text.isBlank()) {
            vm.notify(R.string.msg_nothing_to_copy)
        } else {
            ShareHelper.copy(context, text)
            // Android 13+ shows its own "copied" confirmation.
            if (Build.VERSION.SDK_INT < 33) vm.notify(R.string.msg_copied)
        }
    }
    val sendTo: (SendTarget, String, List<Attachment>) -> Unit = { target, text, files ->
        when (ShareHelper.send(context, target, text, files)) {
            ShareResult.OPENED_APP -> Unit
            ShareResult.OPENED_WEB -> vm.notify(R.string.msg_chatgpt_web)
            ShareResult.OPENED_MENU_FALLBACK -> vm.notify(R.string.msg_app_missing)
            ShareResult.FAILED -> vm.notify(R.string.msg_send_failed)
        }
    }
    val sendConversation: (SendTarget) -> Unit = { target ->
        val text = vm.conversationText()
        val files = vm.conversationAttachments()
        if (text.isBlank() && files.isEmpty()) {
            vm.notify(R.string.msg_nothing_to_share)
        } else {
            sendTo(target, text, files)
        }
    }
    val onSend: () -> Unit = {
        buzz()
        val message = vm.send()
        if (message != null && settings.afterSend != SendTarget.NONE) {
            sendTo(settings.afterSend, message.text, message.attachments)
        }
    }
    val openFile: (Attachment) -> Unit = { attachment ->
        if (!ShareHelper.open(context, attachment)) vm.notify(R.string.msg_no_app_open)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (selecting) {
                val chosen = messages.filter { it.id in selected }
                SelectionBar(
                    count = chosen.size,
                    onClose = leaveSelection,
                    onSend = {
                        if (chosen.isNotEmpty()) {
                            sendTo(
                                SendTarget.SHARE_MENU,
                                chosen.map { it.text }.filter { it.isNotBlank() }.joinToString("\n\n"),
                                chosen.flatMap { it.attachments }
                            )
                        }
                        leaveSelection()
                    },
                    onCopy = {
                        copyText(chosen.map { it.text }.filter { it.isNotBlank() }.joinToString("\n\n"))
                        leaveSelection()
                    },
                    onDelete = {
                        chosen.forEach { vm.deleteMessage(it.id) }
                        leaveSelection()
                    }
                )
            } else TopAppBar(
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
                    Surface(
                        onClick = { vm.toggleLanguage() },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Text(
                            text = if (settings.languageTag == SettingsStore.LANG_BG) "BG" else "EN",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
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
                                text = { Text(stringResource(R.string.menu_share_all)) },
                                onClick = { menuOpen = false; sendConversation(SendTarget.SHARE_MENU) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_chatgpt_all)) },
                                onClick = { menuOpen = false; sendConversation(SendTarget.CHATGPT) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_copy_all)) },
                                onClick = { menuOpen = false; copyText(vm.conversationText()) }
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
                showPunctuationKeys = settings.showPunctuationKeys,
                draft = vm.draft,
                attachments = vm.draftAttachments,
                importing = vm.importing,
                onDraftChange = vm::onDraftChange,
                onAttach = { filePicker.launch(FileKind.PICKER_MIME_TYPES) },
                onCamera = takePhoto,
                templates = settings.templates,
                onTemplate = { vm.useTemplate(it) },
                onEditTemplates = { vm.navigate(Screen.TEMPLATES) },
                onRemoveAttachment = { vm.removeDraftAttachment(it) },
                onMic = onMic,
                onStop = { vm.stopDictation() },
                onClearDraft = { vm.clearDraft() },
                onSend = onSend,
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
                messages.forEachIndexed { index, message ->
                    val newDay = index == 0 ||
                        dayKey(messages[index - 1].timestamp) != dayKey(message.timestamp)
                    if (newDay) {
                        item(key = "day-${message.id}") { DaySeparator(message.timestamp) }
                    }
                    item(key = message.id) {
                        MessageBubble(
                            message = message,
                            selecting = selecting,
                            selected = message.id in selected,
                            quickTargets = settings.quickTargets,
                            canTranslate = Translator.ENABLED,
                            onToggle = {
                                selected = if (message.id in selected) {
                                    selected - message.id
                                } else {
                                    selected + message.id
                                }
                            },
                            onStartSelect = {
                                selecting = true
                                selected = setOf(message.id)
                            },
                            onQuick = { target -> sendTo(target, message.text, message.attachments) },
                            onTranslate = { vm.translateMessage(message.id) },
                            onCopy = { copyText(message.text) },
                            onSendTo = { sendTo(SendTarget.SHARE_MENU, message.text, message.attachments) },
                            onChatGpt = { sendTo(SendTarget.CHATGPT, message.text, message.attachments) },
                            onEdit = { vm.editMessage(message.id) },
                            onDelete = { vm.deleteMessage(message.id) },
                            onOpenFile = openFile
                        )
                    }
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
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(R.drawable.ic_mic),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(44.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
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

private fun dayKey(timestamp: Long): Int {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = timestamp
    return calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)
}

@Composable
private fun DaySeparator(timestamp: Long) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val label = when (dayKey(timestamp)) {
        dayKey(now) -> stringResource(R.string.day_today)
        dayKey(now - 24L * 60 * 60 * 1000) -> stringResource(R.string.day_yesterday)
        else -> DateFormat.getMediumDateFormat(context).format(Date(timestamp))
    }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onSend: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        title = { Text(stringResource(R.string.selection_count, count)) },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_cancel))
            }
        },
        actions = {
            IconButton(onClick = onCopy, enabled = count > 0) {
                Icon(painterResource(R.drawable.ic_copy), stringResource(R.string.msg_action_copy))
            }
            IconButton(onClick = onDelete, enabled = count > 0) {
                Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.msg_action_delete))
            }
            IconButton(onClick = onSend, enabled = count > 0) {
                Icon(painterResource(R.drawable.ic_forward), stringResource(R.string.msg_action_send))
            }
        }
    )
}

@Composable
private fun MessageBubble(
    message: Message,
    selecting: Boolean,
    selected: Boolean,
    quickTargets: List<SendTarget>,
    canTranslate: Boolean,
    onToggle: () -> Unit,
    onStartSelect: () -> Unit,
    onQuick: (SendTarget) -> Unit,
    onTranslate: () -> Unit,
    onCopy: () -> Unit,
    onSendTo: () -> Unit,
    onChatGpt: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenFile: (Attachment) -> Unit
) {
    val context = LocalContext.current
    val scale = LocalMessageScale.current
    var menuOpen by remember { mutableStateOf(false) }
    val time = remember(message.timestamp) {
        DateFormat.getTimeFormat(context).format(Date(message.timestamp))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 40.dp),
        horizontalAlignment = Alignment.End
    ) {
        Box {
            Surface(
                onClick = { if (selecting) onToggle() else menuOpen = true },
                shape = RoundedCornerShape(
                    topStart = 20.dp, topEnd = 20.dp, bottomEnd = 6.dp, bottomStart = 20.dp
                ),
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                contentColor = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
                border = if (selecting && !selected) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                } else {
                    null
                }
            ) {
                Column(
                    modifier = Modifier.padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    message.attachments.forEach { attachment ->
                        val open: () -> Unit = { if (selecting) onToggle() else onOpenFile(attachment) }
                        if (attachment.kind == FileKind.IMAGE) {
                            ImageAttachment(attachment, open)
                        } else {
                            FileAttachment(attachment, open)
                        }
                    }
                    if (message.text.isNotBlank()) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyLarge,
                            fontSize = 16.sp * scale,
                            lineHeight = 22.sp * scale,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.msg_action_send)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_forward), null) },
                    onClick = { menuOpen = false; onSendTo() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.msg_action_chatgpt)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_share), null) },
                    onClick = { menuOpen = false; onChatGpt() }
                )
                if (canTranslate && message.text.isNotBlank()) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.msg_action_translate)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_bolt), null) },
                        onClick = { menuOpen = false; onTranslate() }
                    )
                }
                if (message.text.isNotBlank()) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.msg_action_copy)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_copy), null) },
                        onClick = { menuOpen = false; onCopy() }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.msg_action_edit)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_back), null) },
                    onClick = { menuOpen = false; onEdit() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.msg_action_select)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_more), null) },
                    onClick = { menuOpen = false; onStartSelect() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.msg_action_delete)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_delete), null) },
                    onClick = { menuOpen = false; onDelete() }
                )
            }
        }
        // Under the bubble: the time and one-tap buttons that send just this message.
        Row(
            modifier = Modifier
                .padding(top = 4.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!selecting) {
                quickTargets.forEach { target ->
                    Surface(
                        onClick = { onQuick(target) },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        Text(
                            text = target.title ?: target.name,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
                Surface(
                    onClick = onSendTo,
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.msg_send_short),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            painterResource(R.drawable.ic_forward),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageAttachment(attachment: Attachment, onClick: () -> Unit) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, attachment.path) {
        value = withContext(Dispatchers.IO) { loadThumbnail(attachment.path, 720) }
    }
    val shape = RoundedCornerShape(14.dp)
    val loaded = bitmap
    if (loaded != null) {
        Image(
            bitmap = loaded,
            contentDescription = attachment.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 230.dp, height = 170.dp)
                .clip(shape)
                .clickable(onClick = onClick)
        )
    } else {
        Box(
            modifier = Modifier
                .size(width = 230.dp, height = 170.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = attachment.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
private fun FileAttachment(attachment: Attachment, onClick: () -> Unit) {
    val context = LocalContext.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier
                .widthIn(min = 200.dp, max = 250.dp)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KindBadge(attachment.kind, 42)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = attachment.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = Formatter.formatShortFileSize(context, attachment.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun KindBadge(kind: FileKind, sizeDp: Int) {
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .background(fileKindColor(kind), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = kind.label,
            color = Color.White,
            fontSize = (sizeDp / 3.6f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Bottom panel: status, live words, files, editable draft, punctuation keys, big microphone. */
@Composable
private fun Composer(
    speech: SpeechUiState,
    languageTag: String,
    micActive: Boolean,
    showPunctuationKeys: Boolean,
    draft: TextFieldValue,
    attachments: List<Attachment>,
    importing: Boolean,
    onDraftChange: (TextFieldValue) -> Unit,
    onAttach: () -> Unit,
    onCamera: () -> Unit,
    templates: List<String>,
    onTemplate: (String) -> Unit,
    onEditTemplates: () -> Unit,
    onRemoveAttachment: (Attachment) -> Unit,
    onMic: () -> Unit,
    onStop: () -> Unit,
    onClearDraft: () -> Unit,
    onSend: () -> Unit,
    onPunctuation: (String) -> Unit,
    onDismissNotice: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    val scale = LocalMessageScale.current
    var attachMenu by remember { mutableStateOf(false) }
    var templateMenu by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
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

            if (importing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (attachments.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    attachments.forEach { attachment ->
                        DraftAttachmentChip(attachment) { onRemoveAttachment(attachment) }
                    }
                }
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Box {
                    IconButton(onClick = { attachMenu = true }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            painterResource(R.drawable.ic_attach),
                            contentDescription = stringResource(R.string.cd_attach),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.attach_file)) },
                            onClick = { attachMenu = false; onAttach() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.attach_camera)) },
                            onClick = { attachMenu = false; onCamera() }
                        )
                    }
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.draft_hint)) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp * scale,
                        lineHeight = 22.sp * scale
                    ),
                    shape = RoundedCornerShape(22.dp),
                    maxLines = 5
                )
                Box {
                    IconButton(onClick = { templateMenu = true }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            painterResource(R.drawable.ic_bolt),
                            contentDescription = stringResource(R.string.cd_templates),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    DropdownMenu(expanded = templateMenu, onDismissRequest = { templateMenu = false }) {
                        templates.forEach { template ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = template,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 260.dp)
                                    )
                                },
                                onClick = { templateMenu = false; onTemplate(template) }
                            )
                        }
                        if (templates.isNotEmpty()) HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.templates_edit)) },
                            onClick = { templateMenu = false; onEditTemplates() }
                        )
                    }
                }
            }

            if (showPunctuationKeys) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(".", ",", "?", "!", "\n").forEach { mark ->
                        Surface(
                            onClick = { onPunctuation(mark) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
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
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val sessionOpen = speech.state != DictationState.IDLE
                val hasDraft = draft.text.isNotEmpty() || attachments.isNotEmpty()
                FilledTonalIconButton(
                    onClick = { if (sessionOpen) onStop() else onClearDraft() },
                    enabled = sessionOpen || hasDraft,
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
                    enabled = draft.text.isNotBlank() || attachments.isNotEmpty(),
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

@Composable
private fun DraftAttachmentChip(attachment: Attachment, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KindBadge(attachment.kind, 30)
            Spacer(Modifier.width(8.dp))
            Text(
                text = attachment.name,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 150.dp)
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(
                    painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.cd_remove_attachment),
                    modifier = Modifier.size(16.dp)
                )
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
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
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
            shadowElevation = 6.dp,
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

/** Decodes a small version of an image, respecting the camera's rotation tag. */
private fun loadThumbnail(path: String, maxSide: Int): ImageBitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        var bitmap: Bitmap = BitmapFactory.decodeFile(path, options) ?: return null
        val orientation = ExifInterface(path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees != 0f) {
            val matrix = Matrix().apply { postRotate(degrees) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        bitmap.asImageBitmap()
    } catch (_: Throwable) {
        null
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
