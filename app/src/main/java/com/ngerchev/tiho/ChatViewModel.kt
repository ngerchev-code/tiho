package com.ngerchev.tiho

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ngerchev.tiho.data.AppSettings
import com.ngerchev.tiho.data.Attachment
import com.ngerchev.tiho.data.AttachmentStore
import com.ngerchev.tiho.data.ImportError
import com.ngerchev.tiho.data.Message
import com.ngerchev.tiho.data.MessageStore
import com.ngerchev.tiho.data.SettingsStore
import com.ngerchev.tiho.speech.DictationState
import com.ngerchev.tiho.speech.SpeechController
import com.ngerchev.tiho.speech.SpeechNotice
import com.ngerchev.tiho.speech.SpeechUiState
import com.ngerchev.tiho.speech.TextFormatter
import com.ngerchev.tiho.update.UpdateState
import com.ngerchev.tiho.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { CHAT, LANGUAGE, SETTINGS, ABOUT }

/** The single ViewModel of the app (MVVM): owns the state, the UI only renders it. */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsStore(application)
    private val messageStore = MessageStore(application)
    private val attachmentStore = AttachmentStore(application)
    private val speech = SpeechController(application) { text -> onRecognized(text) }

    val settings: StateFlow<AppSettings> = settingsStore.settings
    val messages: StateFlow<List<Message>> = messageStore.messages
    val speechState: StateFlow<SpeechUiState> = speech.state

    /** The editable draft. Recognized phrases are appended here; the user can correct them. */
    var draft by mutableStateOf(TextFieldValue(""))
        private set

    /** Files waiting to be sent with the next message. */
    val draftAttachments = mutableStateListOf<Attachment>()

    var importing by mutableStateOf(false)
        private set

    var screen by mutableStateOf(Screen.CHAT)
        private set

    /** Self-update progress; stays None in the Google Play edition. */
    var updateState by mutableStateOf<UpdateState>(UpdateState.None)
        private set

    private var lastUpdateCheck = 0L

    private val _events = MutableSharedFlow<Int>(extraBufferCapacity = 8)

    /** One-off messages for the snackbar, as string resource ids. */
    val events: SharedFlow<Int> = _events.asSharedFlow()

    init {
        messageStore.init(settings.value.retention)
        // Auto-delete while the app is open. It also runs on every app start.
        viewModelScope.launch {
            while (isActive) {
                messageStore.purgeExpired()
                delay(60_000L)
            }
        }
        checkForUpdate(manual = false)
    }

    // ---- self-update ----

    /** Looks for a newer build. Automatic checks are silent; a manual one reports the result. */
    fun checkForUpdate(manual: Boolean) {
        if (!Updater.ENABLED) return
        if (updateState is UpdateState.Downloading || updateState is UpdateState.Ready) return
        lastUpdateCheck = System.currentTimeMillis()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Updater.check(getApplication<Application>()) }
            }
            val info = result.getOrNull()
            when {
                info != null -> updateState = UpdateState.Available(info)
                manual && result.isFailure -> notify(R.string.msg_update_check_failed)
                manual -> notify(R.string.msg_up_to_date)
            }
        }
    }

    fun startUpdate() {
        if (updateState is UpdateState.Downloading) return
        updateState = UpdateState.Downloading(0f)
        viewModelScope.launch {
            var lastShown = 0f
            val file = withContext(Dispatchers.IO) {
                Updater.download(getApplication<Application>()) { progress ->
                    // Report in steps of 2% to keep the UI calm.
                    if (progress - lastShown >= 0.02f || progress >= 1f) {
                        lastShown = progress
                        viewModelScope.launch {
                            if (updateState is UpdateState.Downloading) {
                                updateState = UpdateState.Downloading(progress)
                            }
                        }
                    }
                }
            }
            updateState = if (file != null) UpdateState.Ready(file) else UpdateState.Failed
        }
    }

    fun dismissUpdate() {
        if (updateState !is UpdateState.Downloading) updateState = UpdateState.None
    }

    // ---- navigation ----

    fun navigate(target: Screen) {
        if (target != Screen.CHAT) speech.pause()
        screen = target
    }

    // ---- dictation ----

    fun startDictation() {
        val s = settings.value
        speech.start(s.languageTag, s.preferOnDevice)
    }

    fun pauseDictation() = speech.pause()

    fun stopDictation() = speech.stop()

    fun onPermissionDenied() = speech.showNotice(SpeechNotice.NO_PERMISSION)

    fun dismissNotice() = speech.showNotice(null)

    private fun onRecognized(raw: String) {
        val s = settings.value
        val text = if (s.spokenPunctuation) {
            TextFormatter.applySpokenPunctuation(raw, s.languageTag)
        } else {
            raw
        }
        setDraftText(TextFormatter.join(draft.text, text))
        if (s.autoSend) send()
    }

    // ---- draft ----

    fun onDraftChange(value: TextFieldValue) {
        draft = value
    }

    fun insertPunctuation(mark: String) {
        val base = draft.text.trimEnd(' ')
        setDraftText(if (mark == "\n") base + "\n" else "$base$mark ")
    }

    fun clearDraft() {
        setDraftText("")
        val files = draftAttachments.toList()
        draftAttachments.clear()
        discard(files)
    }

    private fun setDraftText(text: String) {
        draft = TextFieldValue(text, TextRange(text.length))
    }

    // ---- attachments ----

    fun addAttachments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        importing = true
        viewModelScope.launch {
            var error: ImportError? = null
            for (uri in uris.take(MAX_FILES_PER_PICK)) {
                val result = withContext(Dispatchers.IO) { attachmentStore.importFile(uri) }
                val attachment = result.attachment
                if (attachment != null) draftAttachments.add(attachment) else error = result.error
            }
            importing = false
            when (error) {
                ImportError.UNSUPPORTED -> notify(R.string.msg_file_unsupported)
                ImportError.TOO_LARGE -> notify(R.string.msg_file_too_large)
                ImportError.FAILED -> notify(R.string.msg_file_failed)
                null -> Unit
            }
        }
    }

    fun removeDraftAttachment(attachment: Attachment) {
        draftAttachments.remove(attachment)
        discard(listOf(attachment))
    }

    private fun discard(files: List<Attachment>) {
        if (files.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            files.forEach { attachmentStore.delete(it) }
        }
    }

    // ---- messages ----

    /** Turns the draft into a message. Returns it, or null when there is nothing to send. */
    fun send(): Message? {
        val text = draft.text.trim()
        val files = draftAttachments.toList()
        if (text.isEmpty() && files.isEmpty()) return null
        val message = messageStore.add(text, files)
        draftAttachments.clear()
        setDraftText("")
        return message
    }

    fun deleteMessage(id: Long) = messageStore.delete(id)

    fun clearConversation() {
        speech.stop()
        messageStore.clear()
        clearDraft()
    }

    fun conversationText(): String =
        messages.value.map { it.text }.filter { it.isNotBlank() }.joinToString("\n\n")

    fun conversationAttachments(): List<Attachment> = messages.value.flatMap { it.attachments }

    fun notify(messageRes: Int) {
        _events.tryEmit(messageRes)
    }

    // ---- settings ----

    fun setLanguage(tag: String) {
        if (tag == settings.value.languageTag) return
        val wasListening = speechState.value.state == DictationState.LISTENING ||
            speechState.value.state == DictationState.STARTING
        if (wasListening) speech.halt(DictationState.PAUSED, null)
        settingsStore.update { it.copy(languageTag = tag) }
        if (wasListening) startDictation()
    }

    fun toggleLanguage() {
        val next = if (settings.value.languageTag == SettingsStore.LANG_BG) {
            SettingsStore.LANG_EN
        } else {
            SettingsStore.LANG_BG
        }
        setLanguage(next)
    }

    /** Changes any setting except the language (which may need to restart dictation). */
    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val before = settings.value
        settingsStore.update(transform)
        val after = settings.value
        if (after.retention != before.retention) messageStore.setRetention(after.retention)
    }

    // ---- lifecycle ----

    fun onForeground() {
        messageStore.purgeExpired()
        // Look for a new version again when the app comes back after a few hours.
        if (System.currentTimeMillis() - lastUpdateCheck > UPDATE_CHECK_INTERVAL_MS) {
            checkForUpdate(manual = false)
        }
    }

    /** The microphone is never left on when the app is not in front. */
    fun onBackground() {
        if (speech.isActive()) speech.halt(DictationState.PAUSED, null)
    }

    override fun onCleared() {
        speech.dispose()
        super.onCleared()
    }

    companion object {
        private const val MAX_FILES_PER_PICK = 10
        private const val UPDATE_CHECK_INTERVAL_MS = 3L * 60 * 60 * 1000
    }
}
