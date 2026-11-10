package com.ngerchev.tiho

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ngerchev.tiho.data.AppSettings
import com.ngerchev.tiho.data.Message
import com.ngerchev.tiho.data.MessageStore
import com.ngerchev.tiho.data.SettingsStore
import com.ngerchev.tiho.speech.DictationState
import com.ngerchev.tiho.speech.SpeechController
import com.ngerchev.tiho.speech.SpeechNotice
import com.ngerchev.tiho.speech.SpeechUiState
import com.ngerchev.tiho.speech.TextFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Screen { CHAT, LANGUAGE, SETTINGS, ABOUT }

/** The single ViewModel of the app (MVVM): owns the state, the UI only renders it. */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsStore(application)
    private val messageStore = MessageStore(application)
    private val speech = SpeechController(application) { text -> onRecognized(text) }

    val settings: StateFlow<AppSettings> = settingsStore.settings
    val messages: StateFlow<List<Message>> = messageStore.messages
    val speechState: StateFlow<SpeechUiState> = speech.state

    /** The editable draft. Recognized phrases are appended here; the user can correct them. */
    var draft by mutableStateOf(TextFieldValue(""))
        private set

    var screen by mutableStateOf(Screen.CHAT)
        private set

    private val _events = MutableSharedFlow<Int>(extraBufferCapacity = 8)

    /** One-off messages for the snackbar, as string resource ids. */
    val events: SharedFlow<Int> = _events.asSharedFlow()

    init {
        messageStore.init(settings.value.keepFor24h)
        // 24-hour auto-delete while the app is open. It also runs on every app start.
        viewModelScope.launch {
            while (isActive) {
                messageStore.purgeExpired()
                delay(60_000L)
            }
        }
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
    }

    // ---- draft ----

    fun onDraftChange(value: TextFieldValue) {
        draft = value
    }

    fun insertPunctuation(mark: String) {
        val base = draft.text.trimEnd(' ')
        setDraftText(if (mark == "\n") base + "\n" else "$base$mark ")
    }

    fun clearDraft() = setDraftText("")

    private fun setDraftText(text: String) {
        draft = TextFieldValue(text, TextRange(text.length))
    }

    // ---- messages ----

    fun send() {
        val text = draft.text.trim()
        if (text.isEmpty()) return
        messageStore.add(text)
        setDraftText("")
    }

    fun deleteMessage(id: Long) = messageStore.delete(id)

    fun clearConversation() {
        speech.stop()
        messageStore.clear()
        setDraftText("")
    }

    fun conversationText(): String = messages.value.joinToString("\n\n") { it.text }

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

    fun setPreferOnDevice(value: Boolean) = settingsStore.update { it.copy(preferOnDevice = value) }

    fun setSpokenPunctuation(value: Boolean) =
        settingsStore.update { it.copy(spokenPunctuation = value) }

    fun setKeepFor24h(value: Boolean) {
        settingsStore.update { it.copy(keepFor24h = value) }
        messageStore.setPersistence(value)
    }

    // ---- lifecycle ----

    fun onForeground() = messageStore.purgeExpired()

    /** The microphone is never left on when the app is not in front. */
    fun onBackground() {
        if (speech.isActive()) speech.halt(DictationState.PAUSED, null)
    }

    override fun onCleared() {
        speech.dispose()
        super.onCleared()
    }
}
