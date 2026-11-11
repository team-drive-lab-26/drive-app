package com.doran.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.doran.app.core.data.NaviState
import com.doran.app.core.data.PleosNavi
import com.doran.app.core.data.VehicleStateSource
import com.doran.app.core.designsystem.DoranMood
import com.doran.app.core.model.Gear
import com.doran.app.core.model.VehicleState
import com.doran.app.core.trip.TripConversationState
import com.doran.app.core.trip.TripCoordinator
import com.doran.app.core.voice.DoranVoice
import com.doran.app.core.voice.ListenPhase
import com.doran.app.core.voice.VoiceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** 화면에서만 쓰는 상태. 디버그 패널, 시연 촬영용 강제 표정, 바깥에서 요청한 화면 이동 */
data class LocalUiState(
    val showDebug: Boolean = false,
    val forcedMood: DoranMood? = null,
    val requestedRoute: String? = null,
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
    val caption: String get() = if (isListening && voice.partial.isNotBlank()) voice.partial else conversation.caption

    val mood: DoranMood
        get() = local.forcedMood ?: when {
            conversation.isThinking -> DoranMood.THINKING
            voice.isSpeaking -> DoranMood.SPEAKING
            isListening -> DoranMood.LISTENING
            else -> DoranMood.IDLE
        }

    /** 주행 화면 위쪽에 두는 길 정보 한 줄 */
    val routeLine: String
        get() = when {
            !navi.isRouteActive || navi.destinationName.isBlank() -> ""
            navi.etaMinutes == null -> navi.destinationName
            else -> "${navi.destinationName}까지 ${formatMinutes(navi.etaMinutes)}"
        }
}

private fun formatMinutes(minutes: Int): String =
    if (minutes < 60) "${minutes}분" else "${minutes / 60}시간 ${minutes % 60}분".removeSuffix(" 0분")

class MainViewModel(
    private val coordinator: TripCoordinator,
    vehicle: VehicleStateSource,
    navi: PleosNavi,
    voice: DoranVoice,
) : ViewModel() {
    private val local = MutableStateFlow(LocalUiState())

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
    fun startDemoRoute() = coordinator.startDemoRoute()
    fun overrideGear(gear: Gear) = coordinator.overrideGear(gear)
    fun overrideDoorOpen(open: Boolean) = coordinator.overrideDoorOpen(open)

    fun toggleDebug() = local.update { it.copy(showDebug = !it.showDebug) }
    fun setDebugVisible(visible: Boolean) = local.update { it.copy(showDebug = visible) }
    fun forceMood(mood: DoranMood?) = local.update { it.copy(forcedMood = mood) }
    fun requestRoute(route: String) = local.update { it.copy(requestedRoute = route) }
    fun onRouteHandled() = local.update { it.copy(requestedRoute = null) }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as DoranApplication).container
                MainViewModel(container.tripCoordinator, container.vehicleStateSource, container.navi, container.voice)
            }
        }
    }
}
