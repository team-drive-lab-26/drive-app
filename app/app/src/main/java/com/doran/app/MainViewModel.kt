package com.doran.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.doran.app.core.data.AgentCard
import com.doran.app.core.data.NaviState
import com.doran.app.core.data.PleosNavi
import com.doran.app.core.data.VehicleStateSource
import com.doran.app.core.designsystem.DoranMood
import com.doran.app.core.media.LocalTrack
import com.doran.app.core.media.MusicPlayer
import com.doran.app.core.model.Gear
import com.doran.app.core.model.VehicleState
import com.doran.app.core.trip.TripConversationState
import com.doran.app.core.trip.TripCoordinator
import com.doran.app.core.voice.DoranVoice
import com.doran.app.core.voice.ListenPhase
import com.doran.app.core.voice.VoiceState
import com.doran.app.core.voice.WakeWord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 화면에서만 쓰는 상태. 디버그 패널, 시연 촬영용 강제 표정, 바깥에서 요청한 화면 이동 */
data class LocalUiState(
    val showDebug: Boolean = false,
    val forcedMood: DoranMood? = null,
    val requestedRoute: String? = null,
    val nowPlaying: String? = null,
    val musicPaused: Boolean = false,
    /** 기기에 넣어 둔 곡 전체. 음악 화면의 다른 곡 목록 */
    val library: List<LocalTrack> = emptyList(),
)

data class MainUiState(
    val vehicle: VehicleState = VehicleState(),
    val navi: NaviState = NaviState(),
    val voice: VoiceState = VoiceState(),
    val conversation: TripConversationState = TripConversationState(),
    val local: LocalUiState = LocalUiState(),
) {
    val isDriving: Boolean get() = vehicle.isDriving

    // 호출어 대기 중에도 마이크는 켜져 있지만, 화면에는 호출됐을 때만 듣는 중으로 보인다
    val isListening: Boolean get() = voice.listenPhase == ListenPhase.LISTENING && conversation.isAwake

    /** 듣는 동안에는 인식 중인 문장을, 아니면 도란의 마지막 말을 보여 준다 */
    val caption: String get() = if (isListening && voice.partial.isNotBlank()) WakeWord.display(voice.partial) else conversation.caption

    val mood: DoranMood
        get() = local.forcedMood ?: when {
            conversation.isThinking -> DoranMood.THINKING
            voice.isSpeaking -> DoranMood.SPEAKING
            isListening -> DoranMood.LISTENING
            else -> DoranMood.IDLE
        }

    /** 목적지 이름. 내비 경로가 없으면 시연용 값 */
    val destination: String
        get() = if (navi.isRouteActive && navi.destinationName.isNotBlank()) navi.destinationName else conversation.demoDestination

    val etaMinutes: Int?
        get() = if (navi.isRouteActive && navi.destinationName.isNotBlank()) navi.etaMinutes else conversation.demoEtaMinutes

    /** 주행 화면 위쪽에 두는 길 정보 한 줄 */
    val routeLine: String
        get() {
            val eta = etaMinutes
            return when {
                destination.isBlank() -> ""
                eta == null -> destination
                else -> "${destination}까지 ${formatMinutes(eta)}"
            }
        }

    /** 남은 시간만. 정차 화면 위 막대에 크게 쓴다 */
    val etaText: String get() = etaMinutes?.let(::formatMinutes) ?: ""

    /** 도착 예정 시각. 위 막대 아래 줄과 지평선 표지판에 쓴다 */
    val arrivalClock: String
        get() = etaMinutes?.let { java.time.LocalTime.now(java.time.ZoneId.of("Asia/Seoul")).plusMinutes(it.toLong()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) } ?: ""
}

private fun formatMinutes(minutes: Int): String =
    if (minutes < 60) "${minutes}분" else "${minutes / 60}시간 ${minutes % 60}분".removeSuffix(" 0분")

class MainViewModel(
    private val coordinator: TripCoordinator,
    private val vehicle: VehicleStateSource,
    navi: PleosNavi,
    voice: DoranVoice,
    private val music: MusicPlayer,
) : ViewModel() {
    private val local = MutableStateFlow(LocalUiState())

    init {
        viewModelScope.launch { music.nowPlaying.collect { id -> local.update { it.copy(nowPlaying = id) } } }
        viewModelScope.launch { music.paused.collect { paused -> local.update { it.copy(musicPaused = paused) } } }
        viewModelScope.launch { music.library.collect { list -> local.update { it.copy(library = list) } } }
    }

    val uiState: StateFlow<MainUiState> = combine(
        vehicle.state,
        navi.state,
        voice.state,
        coordinator.state,
        local,
        ::MainUiState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(),
    )

    fun onListenClick() {
        if (uiState.value.isListening) coordinator.cancelListening() else coordinator.listen()
    }

    fun onMicPermissionDenied() = coordinator.onMicPermissionDenied()
    fun enableHandsFree() = coordinator.enableHandsFree()
    fun submitUtterance(text: String) = coordinator.submitUtterance(text)
    fun sendDemoEvent(event: String) = coordinator.sendDemoEvent(event)
    fun debugArrive(name: String) = coordinator.debugArrive(name)
    fun startDemoRoute() = coordinator.startDemoRoute()
    fun seedDemoTape() = coordinator.seedDemoTape()
    fun stopTalking() = coordinator.stopTalking()
    fun showDebugCaption(text: String) = coordinator.showDebugCaption(text)
    fun showDebugDetour(name: String) = coordinator.showDebugDetour(name)
    fun overrideGear(gear: Gear) = coordinator.overrideGear(gear)
    fun overrideDoorOpen(open: Boolean) = coordinator.overrideDoorOpen(open)

    /** 디버그용. 값이 null이면 그대로 두고, "-"면 지운다 */
    fun overrideVehicleExtras(battery: String?, seats: String?, wiper: String?) =
        vehicle.overrideExtras { state ->
            state.copy(
                batteryPercent = if (battery == null) state.batteryPercent else battery.toIntOrNull(),
                buckledSeats = if (seats == null) state.buckledSeats else seats.toIntOrNull(),
                isWiperOn = if (wiper == null) state.isWiperOn else wiper == "on",
            )
        }

    // 음악 화면에서 누른 곡은 서버를 거치지 않고 바로 튼다
    fun playTrack(trackId: String) = music.play(trackId)
    fun pauseMusic() = music.pause()
    fun resumeMusic() = music.resume()

    fun chooseCard(card: AgentCard) = coordinator.chooseCard(card)
    fun dismissCards() = coordinator.dismissCards()
    fun answerRecord(yes: Boolean) = coordinator.answerRecord(yes)
    fun requestMusic() = coordinator.requestMusic()
    fun toggleDebug() = local.update { it.copy(showDebug = !it.showDebug) }
    fun setDebugVisible(visible: Boolean) = local.update { it.copy(showDebug = visible) }
    fun forceMood(mood: DoranMood?) = local.update { it.copy(forcedMood = mood) }
    fun requestRoute(route: String) = local.update { it.copy(requestedRoute = route) }
    fun onRouteHandled() = local.update { it.copy(requestedRoute = null) }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as DoranApplication).container
                MainViewModel(
                    container.tripCoordinator,
                    container.vehicleStateSource,
                    container.navi,
                    container.voice,
                    container.musicPlayer,
                )
            }
        }
    }
}
