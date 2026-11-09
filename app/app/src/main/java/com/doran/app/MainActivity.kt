package com.doran.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.doran.app.core.data.AgentClient
import com.doran.app.core.data.FakeVehicleStateSource
import com.doran.app.core.data.PleosWaypointController
import com.doran.app.core.designsystem.AgentDebugPanel
import com.doran.app.core.designsystem.DebugVehicleBar
import com.doran.app.core.designsystem.DoranTheme
import com.doran.app.core.media.MusicPlayer
import com.doran.app.navigation.DoranNavHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val vehicle = FakeVehicleStateSource()
    private val caption = mutableStateOf("")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val sessionId = UUID.randomUUID().toString()
    private val agentClient = AgentClient(BuildConfig.AGENT_BASE_URL)
    private var recognizer: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech
    private lateinit var waypointController: PleosWaypointController
    private var ttsReady = false
    private lateinit var musicPlayer: MusicPlayer
    private var pendingTrackId: String? = null
    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening() else caption.value = "마이크 권한이 필요해"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        musicPlayer = MusicPlayer(this)
        waypointController = PleosWaypointController(this) {
            runOnUiThread {
                caption.value = "내비에서 경유지를 추가하지 못했어."
                speak(caption.value)
            }
        }
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) tts.language = Locale.KOREAN
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                if (utteranceId == REPLY_UTTERANCE) runOnUiThread(::playPendingTrack)
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == REPLY_UTTERANCE) runOnUiThread(::playPendingTrack)
            }
        })
        setContent {
            DoranTheme {
                val state by vehicle.state.collectAsStateWithLifecycle()
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        DoranNavHost(
                            isDriving = state.isDriving,
                            caption = caption.value,
                            onListen = ::requestListening,
                        )
                    }
                    if (BuildConfig.DEBUG) {
                        if (!state.isDriving) {
                            AgentDebugPanel(
                                caption = caption.value,
                                onSubmit = ::submitUtterance,
                                onDemoEvent = ::submitDemoEvent,
                            )
                        }
                        DebugVehicleBar(gear = state.gear, onGearChange = vehicle::setGear)
                    }
                }
            }
        }
    }

    private fun submitUtterance(text: String) {
        if (BuildConfig.AGENT_BASE_URL.isBlank()) {
            caption.value = "AI 서버 설정이 필요해"
            return
        }
        scope.launch {
            caption.value = "도란이 듣고 있어"
            try {
                val reply = agentClient.send(sessionId, vehicle.state.value.gear.name, utterance = text)
                var spoken = reply.speech
                reply.waypoint?.let { waypoint ->
                    waypointController.add(waypoint) { submitted ->
                        runOnUiThread {
                            val message = if (submitted) "경유지 추가를 요청했어." else "내비 연결을 확인해 줘."
                            caption.value = message
                            speak(message)
                        }
                    }
                    spoken = ""
                }
                reply.requestedTrackId?.let { trackId ->
                    if (musicPlayer.canPlay(trackId)) {
                        // 안내 음성과 겹치지 않게 말이 끝난 뒤 튼다
                        pendingTrackId = trackId
                    } else {
                        spoken = "이 곡은 아직 들을 수 없어."
                    }
                }
                if (reply.requestedCapsuleId != null) spoken = "녹음 재생 연결이 필요해."
                if (reply.waypoint == null) caption.value = spoken.ifBlank { reply.caption.ifBlank { "다시 말해 줘" } }
                if (!speak(spoken)) playPendingTrack()
            } catch (_: Exception) {
                caption.value = "AI 서버에 연결할 수 없어"
                speak(caption.value)
            }
        }
    }

    private fun submitDemoEvent(event: String) {
        scope.launch {
            try {
                val vehicleExtras = org.json.JSONObject()
                val navigation = org.json.JSONObject()
                if (event == "trip_started") navigation.put("distance_from_home_km", 120)
                if (event == "continuous_drive") vehicleExtras.put("continuous_driving_minutes", 130)
                val reply = agentClient.send(
                    sessionId,
                    vehicle.state.value.gear.name,
                    event = event,
                    vehicleExtras = vehicleExtras,
                    navigation = navigation,
                )
                caption.value = reply.caption.ifBlank { "이번에는 제안이 없어" }
                speak(reply.speech)
            } catch (_: Exception) {
                caption.value = "AI 서버에 연결할 수 없어"
            }
        }
    }

    private fun speak(text: String): Boolean {
        if (!ttsReady || text.isBlank()) return false
        return tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, REPLY_UTTERANCE) == TextToSpeech.SUCCESS
    }

    private fun playPendingTrack() {
        val trackId = pendingTrackId ?: return
        pendingTrackId = null
        musicPlayer.play(trackId)
    }

    private fun requestListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else {
            audioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            caption.value = "음성 인식을 사용할 수 없어"
            return
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { caption.value = "듣고 있어" }
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onError(error: Int) { caption.value = "다시 말해 줘" }
                    override fun onResults(results: Bundle?) {
                        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        if (text.isNullOrBlank()) caption.value = "다시 말해 줘" else submitUtterance(text)
                    }
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
            }
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
        }
        recognizer?.startListening(intent)
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts.shutdown()
        waypointController.release()
        musicPlayer.release()
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val REPLY_UTTERANCE = "doran-reply"
    }
}
