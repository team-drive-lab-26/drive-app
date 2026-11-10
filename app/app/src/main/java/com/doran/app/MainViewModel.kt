package com.doran.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.doran.app.core.data.NaviState
import com.doran.app.core.data.PleosNavi
import com.doran.app.core.data.VehicleStateSource
import com.doran.app.core.model.Gear
import com.doran.app.core.model.VehicleState
import com.doran.app.core.trip.TripConversationState
import com.doran.app.core.trip.TripCoordinator
import com.doran.app.core.voice.DoranVoice
import com.doran.app.core.voice.ListenPhase
import com.doran.app.core.voice.VoiceState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class MainUiState(
    val vehicle: VehicleState = VehicleState(),
    val navi: NaviState = NaviState(),
    val voice: VoiceState = VoiceState(),
    val conversation: TripConversationState = TripConversationState(),
) {
    val isDriving: Boolean get() = vehicle.isDriving
    val isListening: Boolean get() = voice.listenPhase == ListenPhase.LISTENING

    /** 듣는 동안에는 인식 중인 문장을, 아니면 도란의 마지막 말을 한 줄로 보여 준다 */
    val caption: String get() = if (isListening && voice.partial.isNotBlank()) voice.partial else conversation.caption
}

class MainViewModel(
    private val coordinator: TripCoordinator,
    vehicle: VehicleStateSource,
    navi: PleosNavi,
    voice: DoranVoice,
) : ViewModel() {
    val uiState: StateFlow<MainUiState> = combine(
        vehicle.state,
        navi.state,
        voice.state,
        coordinator.state,
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
    fun submitUtterance(text: String) = coordinator.submitUtterance(text)
    fun sendDemoEvent(event: String) = coordinator.sendDemoEvent(event)
    fun startDemoRoute() = coordinator.startDemoRoute()
    fun overrideGear(gear: Gear) = coordinator.overrideGear(gear)
    fun overrideDoorOpen(open: Boolean) = coordinator.overrideDoorOpen(open)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as DoranApplication).container
                MainViewModel(container.tripCoordinator, container.vehicleStateSource, container.navi, container.voice)
            }
        }
    }
}
