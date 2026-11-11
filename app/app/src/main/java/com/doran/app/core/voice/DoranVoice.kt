package com.doran.app.core.voice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import kotlin.math.abs
import ai.pleos.playground.stt.SpeechToText as PleosStt
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInitListener
import ai.pleos.playground.stt.listener.ResultListener as SttResultListener
import ai.pleos.playground.tts.TextToSpeech as PleosTts
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.EventListener as TtsEventListener
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInitListener
import android.speech.tts.TextToSpeech as AndroidTts

enum class VoiceEngine { NONE, PLEOS, ANDROID }

enum class ListenPhase { IDLE, LISTENING }

data class VoiceState(
    val speaker: VoiceEngine = VoiceEngine.NONE,
    val listener: VoiceEngine = VoiceEngine.NONE,
    val isSpeaking: Boolean = false,
    val listenPhase: ListenPhase = ListenPhase.IDLE,
    /** 듣는 중 실시간으로 바뀌는 인식 문장 */
    val partial: String = "",
    /** 0~1. 듣는 중에는 마이크 소리, 말하는 중에는 도란 목소리 크기. 캐릭터 움직임에 쓴다 */
    val level: Float = 0f,
)

sealed interface ListenResult {
    data class Heard(val text: String) : ListenResult
    data object NothingHeard : ListenResult
    data object Unavailable : ListenResult
}

/**
 * 도란의 말하기와 듣기. Pleos 음성 SDK를 온디바이스 모드로 먼저 쓴다(앱 등록 키가 필요 없다).
 * Pleos 음성 서비스가 없는 기기에서는 Android 기본 음성으로 넘어간다.
 * 모든 콜백은 메인 스레드에서 부른다.
 */
class DoranVoice(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(VoiceState())
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private var pleosTts: PleosTts? = null
    private var androidTts: AndroidTts? = null
    private var androidTtsReady = false
    private var onSpoken: (() -> Unit)? = null

    private var pleosStt: PleosStt? = null
    private var androidStt: SpeechRecognizer? = null
    private var onHeard: ((ListenResult) -> Unit)? = null

    private val ttsEvents = object : TtsEventListener {
        override fun onReady() = Unit
        override fun onStart() = Unit
        override fun onDone() = onMain { finishSpeaking(notify = true) }
        override fun onStop() = Unit
        override fun onError(errorType: TtsError) = onMain { finishSpeaking(notify = true) }
        override fun onUpdatedRms(rms: Double) = onMain { setLevel(rms.toFloat()) }
    }

    private val sttResults = object : SttResultListener {
        override fun onReady() = Unit
        override fun onStartedRecognition() = Unit
        override fun onUpdated(stt: String, completed: Boolean) = onMain {
            if (completed) {
                if (stt.isBlank()) deliver(ListenResult.NothingHeard) else deliver(ListenResult.Heard(stt.trim()))
            } else {
                _state.update { it.copy(partial = stt) }
            }
        }

        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = onMain { setLevel(rms.toFloat()) }

        // 끝 신호가 최종 결과보다 먼저 올 때가 있어, 잠시 기다린 뒤에도 결과가 없으면 들은 만큼으로 끝낸다
        override fun onEndedRecognition() = onMain { main.postDelayed(endWithoutFinal, FINAL_RESULT_GRACE_MS) }
        override fun onError(errorType: SttError) = onMain { deliver(ListenResult.NothingHeard) }
    }

    fun start() {
        startPleosTts()
        startPleosStt()
    }

    // ---- 말하기 ----

    /** 말이 끝나거나 실패하면 [onDone]을 부른다. 말할 수 없으면 false를 돌려주고 [onDone]은 부르지 않는다 */
    fun speak(text: String, onDone: () -> Unit = {}): Boolean {
        if (text.isBlank()) return false
        finishSpeaking(notify = false)
        val engine = _state.value.speaker
        val started = when (engine) {
            VoiceEngine.PLEOS -> runCatching { pleosTts!!.speak(text, true) }.isSuccess
            VoiceEngine.ANDROID -> androidTtsReady &&
                androidTts?.speak(text, AndroidTts.QUEUE_FLUSH, null, UTTERANCE_ID) == AndroidTts.SUCCESS
            VoiceEngine.NONE -> false
        }
        if (!started) return false
        onSpoken = onDone
        _state.update { it.copy(isSpeaking = true) }
        return true
    }

    /** 호출을 알아들었다는 짧은 효과음 */
    fun chime() {
        val tone = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, CHIME_VOLUME) }.getOrNull() ?: return
        tone.startTone(ToneGenerator.TONE_PROP_ACK, CHIME_MS)
        main.postDelayed({ tone.release() }, CHIME_MS + 100L)
    }

    fun stopSpeaking() {
        runCatching { pleosTts?.stop() }
        androidTts?.stop()
        finishSpeaking(notify = false)
    }

    private fun finishSpeaking(notify: Boolean) {
        val callback = onSpoken
        onSpoken = null
        _state.update { it.copy(isSpeaking = false, level = 0f) }
        if (notify) callback?.invoke()
    }

    private fun startPleosTts() {
        val tts = runCatching { PleosTts(appContext, TtsMode.ON_DEVICE) }.getOrNull()
        if (tts == null) {
            startAndroidTts()
            return
        }
        pleosTts = tts
        runCatching {
            tts.initialize(object : TtsInitListener {
                override fun onSuccess() = onMain {
                    // 리스너는 초기화가 끝난 뒤에만 등록할 수 있다
                    tts.addEventListener(ttsEvents)
                    runCatching { tts.setLocale(Locale.KOREA) }
                    _state.update { it.copy(speaker = VoiceEngine.PLEOS) }
                }

                override fun onFailure(errorType: TtsError) = onMain {
                    releasePleosTts()
                    startAndroidTts()
                }
            })
        }.onFailure {
            releasePleosTts()
            startAndroidTts()
        }
    }

    private fun releasePleosTts() {
        runCatching { pleosTts?.release() }
        pleosTts = null
    }

    private fun startAndroidTts() {
        if (androidTts != null) return
        androidTts = AndroidTts(appContext) { status ->
            androidTtsReady = status == AndroidTts.SUCCESS
            if (androidTtsReady) {
                androidTts?.language = Locale.KOREAN
                _state.update { it.copy(speaker = VoiceEngine.ANDROID) }
            }
        }.apply {
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = onMain { finishSpeaking(notify = true) }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = onMain { finishSpeaking(notify = true) }
            })
        }
    }

    // ---- 듣기 ----

    /** 한 문장을 듣는다. 결과는 [onResult]로 한 번만 온다 */
    fun listen(onResult: (ListenResult) -> Unit) {
        stopSpeaking()
        cancelListening()
        onHeard = onResult
        val started = when (_state.value.listener) {
            VoiceEngine.PLEOS -> runCatching { pleosStt!!.request() }.isSuccess
            VoiceEngine.ANDROID -> startAndroidListening()
            VoiceEngine.NONE -> false
        }
        if (!started) {
            deliver(ListenResult.Unavailable)
            return
        }
        _state.update { it.copy(listenPhase = ListenPhase.LISTENING, partial = "") }
        main.postDelayed(silenceTimeout, SILENCE_TIMEOUT_MS)
        main.postDelayed(listenTimeout, LISTEN_TIMEOUT_MS)
    }

    fun cancelListening() {
        stopRecognizers()
        onHeard = null
        clearListenTimeouts()
        _state.update { it.copy(listenPhase = ListenPhase.IDLE, partial = "") }
    }

    private fun stopRecognizers() {
        runCatching { pleosStt?.stop() }
        androidStt?.cancel()
    }

    // 조용한 차 안에서는 인식이 끝나지 않을 수 있어 앱에서 시간을 제한한다
    private val silenceTimeout = Runnable {
        if (_state.value.partial.isBlank()) {
            stopRecognizers()
            deliver(ListenResult.NothingHeard)
        }
    }

    private val listenTimeout = Runnable {
        val partial = _state.value.partial.trim()
        stopRecognizers()
        deliver(if (partial.isBlank()) ListenResult.NothingHeard else ListenResult.Heard(partial))
    }

    private val endWithoutFinal = Runnable {
        val partial = _state.value.partial.trim()
        deliver(if (partial.isBlank()) ListenResult.NothingHeard else ListenResult.Heard(partial))
    }

    private fun clearListenTimeouts() {
        main.removeCallbacks(silenceTimeout)
        main.removeCallbacks(listenTimeout)
        main.removeCallbacks(endWithoutFinal)
    }

    private fun deliver(result: ListenResult) {
        val callback = onHeard ?: return
        onHeard = null
        clearListenTimeouts()
        _state.update { it.copy(listenPhase = ListenPhase.IDLE, partial = "", level = 0f) }
        callback(result)
    }

    private fun startPleosStt() {
        val stt = runCatching { PleosStt(appContext, SttMode.ON_DEVICE) }.getOrNull()
        if (stt == null) {
            useAndroidStt()
            return
        }
        pleosStt = stt
        runCatching {
            stt.initialize(object : SttInitListener {
                override fun onSuccess() = onMain {
                    stt.addListener(sttResults)
                    runCatching { stt.setLocale(Locale.KOREA) }
                    _state.update { it.copy(listener = VoiceEngine.PLEOS) }
                }

                override fun onFailure(errorType: SttError) = onMain {
                    releasePleosStt()
                    useAndroidStt()
                }
            })
        }.onFailure {
            releasePleosStt()
            useAndroidStt()
        }
    }

    private fun releasePleosStt() {
        runCatching { pleosStt?.release() }
        pleosStt = null
    }

    private fun useAndroidStt() {
        if (SpeechRecognizer.isRecognitionAvailable(appContext)) {
            _state.update { it.copy(listener = VoiceEngine.ANDROID) }
        }
    }

    private fun startAndroidListening(): Boolean {
        val recognizer = androidStt ?: SpeechRecognizer.createSpeechRecognizer(appContext).also { recognizer ->
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                // 안드로이드 인식기는 대략 -2~10dB를 준다
                override fun onRmsChanged(rmsdB: Float) = setLevel((rmsdB + 2f) / 12f)
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onError(error: Int) = deliver(ListenResult.NothingHeard)
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    deliver(if (text.isNullOrBlank()) ListenResult.NothingHeard else ListenResult.Heard(text))
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!text.isNullOrBlank()) _state.update { it.copy(partial = text) }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            androidStt = recognizer
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        return runCatching { recognizer.startListening(intent) }.isSuccess
    }

    // SDK마다 크기 단위가 달라, 1보다 큰 값은 dB 비슷한 값으로 보고 0~1로 줄인다. 작은 변화는 무시해 화면 갱신을 줄인다
    private fun setLevel(raw: Float) {
        val value = (if (raw > 1f) raw / 12f else raw).coerceIn(0f, 1f)
        if (abs(value - _state.value.level) < LEVEL_STEP) return
        _state.update { it.copy(level = value) }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    fun release() {
        cancelListening()
        stopSpeaking()
        releasePleosTts()
        releasePleosStt()
        androidTts?.shutdown()
        androidTts = null
        androidStt?.destroy()
        androidStt = null
    }

    private companion object {
        const val UTTERANCE_ID = "doran-reply"
        const val SILENCE_TIMEOUT_MS = 8_000L
        const val LISTEN_TIMEOUT_MS = 15_000L
        const val FINAL_RESULT_GRACE_MS = 500L
        const val CHIME_VOLUME = 80
        const val CHIME_MS = 150
        const val LEVEL_STEP = 0.05f
    }
}
