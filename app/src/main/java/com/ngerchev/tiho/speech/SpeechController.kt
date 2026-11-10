package com.ngerchev.tiho.speech

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class DictationState { IDLE, STARTING, LISTENING, PAUSED }

/** Which recognition path is in use right now. */
enum class SpeechEngine { ON_DEVICE, OFFLINE_PREFERRED, ONLINE }

enum class SpeechNotice {
    NO_PERMISSION, UNAVAILABLE, NETWORK, AUDIO, BUSY, LANGUAGE,
    SILENCE, PHONE_CALL, FELL_BACK_ONLINE, GENERIC
}

data class SpeechUiState(
    val state: DictationState = DictationState.IDLE,
    val partial: String = "",
    val engine: SpeechEngine? = null,
    val notice: SpeechNotice? = null
)

/**
 * Wraps Android's SpeechRecognizer for continuous dictation.
 *
 * - Audio is never touched by this app: the recognizer service owns the microphone stream
 *   and hands back text only.
 * - The recognizer stops by itself after each phrase, so while the user wants to keep
 *   dictating it is restarted automatically. After a few silent rounds it pauses to save battery.
 * - Tries on-device recognition first (when preferred and supported) and falls back to the
 *   default recognizer when the language is not available locally.
 *
 * All methods must be called on the main thread; all callbacks arrive on the main thread.
 */
class SpeechController(
    context: Context,
    private val onText: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _state = MutableStateFlow(SpeechUiState())
    val state: StateFlow<SpeechUiState> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private var token = 0
    private var engine = SpeechEngine.ONLINE
    private val localFailed = HashSet<String>()

    private var languageTag = "bg-BG"
    private var preferOnDevice = true

    private var wantListening = false
    private var stopping = false
    private var afterStop = DictationState.PAUSED
    private var lastPartial = ""
    private var silentRounds = 0
    private var retries = 0

    private var modeListener: AudioManager.OnModeChangedListener? = null

    private val restartRunnable = Runnable { if (wantListening) beginListening() }
    private val stopTimeout = Runnable { if (stopping) finishStop() }

    init {
        // Pause as soon as the phone starts ringing or a call begins (Android 12+).
        if (Build.VERSION.SDK_INT >= 31) {
            val listener = AudioManager.OnModeChangedListener { mode ->
                if (isCallMode(mode) && isActive()) {
                    halt(DictationState.PAUSED, SpeechNotice.PHONE_CALL)
                }
            }
            try {
                audioManager.addOnModeChangedListener(appContext.mainExecutor, listener)
                modeListener = listener
            } catch (_: Exception) {
            }
        }
    }

    fun isActive(): Boolean = wantListening || stopping

    fun start(tag: String, prefer: Boolean) {
        if (wantListening) return
        if (stopping) finishStop()
        languageTag = tag
        preferOnDevice = prefer
        if (isCallMode(audioManager.mode)) {
            _state.value = SpeechUiState(DictationState.PAUSED, notice = SpeechNotice.PHONE_CALL)
            return
        }
        wantListening = true
        silentRounds = 0
        retries = 0
        _state.value = SpeechUiState(DictationState.STARTING)
        beginListening()
    }

    /** Stops the microphone but keeps the session "paused"; any pending words are still delivered. */
    fun pause() = requestStop(DictationState.PAUSED)

    /** Ends the dictation session. */
    fun stop() = requestStop(DictationState.IDLE)

    /** Immediate stop without waiting for a final result (app in background, phone call, errors). */
    fun halt(target: DictationState, notice: SpeechNotice?) {
        handler.removeCallbacks(restartRunnable)
        handler.removeCallbacks(stopTimeout)
        wantListening = false
        stopping = false
        commitPartial()
        release()
        _state.value = SpeechUiState(target, notice = notice)
    }

    fun showNotice(notice: SpeechNotice?) {
        _state.update { it.copy(notice = notice) }
    }

    fun dispose() {
        halt(DictationState.IDLE, null)
        if (Build.VERSION.SDK_INT >= 31) {
            val listener = modeListener
            if (listener != null) {
                try {
                    audioManager.removeOnModeChangedListener(listener)
                } catch (_: Exception) {
                }
            }
            modeListener = null
        }
    }

    // ---------------------------------------------------------------------------------------

    private fun isCallMode(mode: Int): Boolean =
        mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_RINGTONE

    private fun requestStop(target: DictationState) {
        handler.removeCallbacks(restartRunnable)
        if (!wantListening) {
            if (stopping) {
                afterStop = target
            } else {
                _state.update { it.copy(state = target, partial = "") }
            }
            return
        }
        wantListening = false
        afterStop = target
        val r = recognizer
        if (r == null) {
            finishStop()
            return
        }
        stopping = true
        _state.update { it.copy(state = target) }
        try {
            r.stopListening()
        } catch (_: Exception) {
        }
        // Safety net: if the service never answers, close the session ourselves.
        handler.postDelayed(stopTimeout, STOP_TIMEOUT_MS)
    }

    private fun finishStop() {
        handler.removeCallbacks(stopTimeout)
        stopping = false
        commitPartial()
        release()
        _state.update { it.copy(state = afterStop, partial = "") }
    }

    private fun commitPartial() {
        val pending = lastPartial.trim()
        lastPartial = ""
        if (pending.isNotEmpty()) onText(pending)
    }

    private fun release() {
        val r = recognizer ?: return
        recognizer = null
        token++ // callbacks from the old instance are ignored from now on
        handler.post {
            try {
                r.cancel()
                r.destroy()
            } catch (_: Exception) {
            }
        }
    }

    private fun createRecognizer(): SpeechRecognizer? {
        val wantLocal = preferOnDevice && languageTag !in localFailed
        var created: SpeechRecognizer? = null
        try {
            if (wantLocal && Build.VERSION.SDK_INT >= 31 &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
            ) {
                engine = SpeechEngine.ON_DEVICE
                created = SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            } else if (SpeechRecognizer.isRecognitionAvailable(appContext)) {
                engine = if (wantLocal) SpeechEngine.OFFLINE_PREFERRED else SpeechEngine.ONLINE
                created = SpeechRecognizer.createSpeechRecognizer(appContext)
            }
        } catch (_: Exception) {
            created = null
        }
        if (created != null) {
            token++
            created.setRecognitionListener(Listener(token))
            recognizer = created
        }
        return created
    }

    private fun beginListening() {
        handler.removeCallbacks(restartRunnable)
        val r = recognizer ?: createRecognizer()
        if (r == null) {
            wantListening = false
            _state.value = SpeechUiState(DictationState.IDLE, notice = SpeechNotice.UNAVAILABLE)
            return
        }
        lastPartial = ""
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
            if (engine == SpeechEngine.OFFLINE_PREFERRED) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                // Ask the service to add punctuation and capitalisation where it can.
                putExtra(
                    RecognizerIntent.EXTRA_ENABLE_FORMATTING,
                    RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY
                )
            }
        }
        try {
            r.startListening(intent)
            _state.update {
                it.copy(
                    state = if (it.state == DictationState.LISTENING) DictationState.LISTENING
                    else DictationState.STARTING,
                    engine = engine,
                    partial = ""
                )
            }
        } catch (_: Exception) {
            halt(DictationState.IDLE, SpeechNotice.GENERIC)
        }
    }

    private fun afterRound() {
        when {
            stopping -> finishStop()
            !wantListening -> Unit
            silentRounds >= MAX_SILENT_ROUNDS -> halt(DictationState.PAUSED, SpeechNotice.SILENCE)
            else -> handler.postDelayed(restartRunnable, RESTART_DELAY_MS)
        }
    }

    private fun fallBackToOnline() {
        localFailed.add(languageTag)
        retries = 0
        commitPartial()
        release()
        _state.update { it.copy(notice = SpeechNotice.FELL_BACK_ONLINE, partial = "") }
        handler.postDelayed(restartRunnable, RETRY_DELAY_MS)
    }

    private fun handleError(code: Int) {
        if (stopping) {
            finishStop()
            return
        }
        if (!wantListening) return

        when (code) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                if (lastPartial.isNotBlank()) {
                    commitPartial()
                    silentRounds = 0
                } else {
                    silentRounds++
                }
                _state.update { it.copy(partial = "") }
                afterRound()
            }

            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                halt(DictationState.IDLE, SpeechNotice.NO_PERMISSION)

            SpeechRecognizer.ERROR_AUDIO -> halt(
                DictationState.PAUSED,
                if (isCallMode(audioManager.mode)) SpeechNotice.PHONE_CALL else SpeechNotice.AUDIO
            )

            else -> {
                if (engine != SpeechEngine.ONLINE && code in LOCAL_FALLBACK_ERRORS) {
                    fallBackToOnline()
                    return
                }
                when (code) {
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        halt(DictationState.PAUSED, SpeechNotice.NETWORK)

                    ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE ->
                        halt(DictationState.PAUSED, SpeechNotice.LANGUAGE)

                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                    SpeechRecognizer.ERROR_CLIENT,
                    SpeechRecognizer.ERROR_SERVER,
                    ERROR_SERVER_DISCONNECTED -> {
                        if (retries < MAX_RETRIES) {
                            retries++
                            commitPartial()
                            release()
                            _state.update { it.copy(partial = "") }
                            handler.postDelayed(restartRunnable, RETRY_DELAY_MS)
                        } else if (engine != SpeechEngine.ONLINE) {
                            fallBackToOnline()
                        } else {
                            halt(
                                DictationState.PAUSED,
                                if (code == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) SpeechNotice.BUSY
                                else SpeechNotice.GENERIC
                            )
                        }
                    }

                    else -> halt(DictationState.PAUSED, SpeechNotice.GENERIC)
                }
            }
        }
    }

    private fun firstResult(bundle: Bundle?): String =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

    private inner class Listener(private val id: Int) : RecognitionListener {
        private val live: Boolean get() = id == token

        override fun onReadyForSpeech(params: Bundle?) {
            if (!live || !wantListening) return
            _state.update { it.copy(state = DictationState.LISTENING, engine = engine) }
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            if (!live) return
            val text = firstResult(partialResults)
            if (text.isNotBlank()) {
                lastPartial = text
                _state.update { it.copy(partial = text) }
            }
        }

        override fun onResults(results: Bundle?) {
            if (!live) return
            val text = firstResult(results).ifBlank { lastPartial }.trim()
            lastPartial = ""
            if (text.isNotEmpty()) {
                onText(text)
                silentRounds = 0
            } else {
                silentRounds++
            }
            retries = 0
            _state.update { it.copy(partial = "") }
            afterRound()
        }

        override fun onError(error: Int) {
            if (!live) return
            handleError(error)
        }
    }

    companion object {
        private const val MAX_SILENT_ROUNDS = 3
        private const val MAX_RETRIES = 3
        private const val RESTART_DELAY_MS = 150L
        private const val RETRY_DELAY_MS = 400L
        private const val STOP_TIMEOUT_MS = 1500L

        // Numeric values of SpeechRecognizer constants added in newer API levels.
        private const val ERROR_SERVER_DISCONNECTED = 11
        private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        private const val ERROR_LANGUAGE_UNAVAILABLE = 13
        private const val ERROR_CANNOT_CHECK_SUPPORT = 14
        private const val ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS = 15

        private val LOCAL_FALLBACK_ERRORS = setOf(
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER,
            ERROR_SERVER_DISCONNECTED,
            ERROR_LANGUAGE_NOT_SUPPORTED,
            ERROR_LANGUAGE_UNAVAILABLE,
            ERROR_CANNOT_CHECK_SUPPORT,
            ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS
        )
    }
}
