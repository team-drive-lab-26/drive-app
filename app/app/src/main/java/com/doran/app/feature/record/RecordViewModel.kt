package com.doran.app.feature.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.doran.app.DoranApplication
import com.doran.app.core.data.AppPreferences
import com.doran.app.core.data.VehicleStateSource
import com.doran.app.core.media.CapsuleRecorder
import com.doran.app.core.media.CapsuleStore
import com.doran.app.core.media.MusicPlayer
import com.doran.app.core.trip.TripCoordinator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class RecordPhase { RECORDING, CHOOSE_SPEAKER, FAILED, DONE }

data class RecordUiState(
    val phase: RecordPhase = RecordPhase.RECORDING,
    val elapsedMs: Long = 0L,
    val level: Float = 0f,
    val family: List<String> = emptyList(),
    val placeName: String = "",
    /** 녹음 때문에 멈춘 것. 왜 조용해졌는지 화면에 한 줄로 알린다 */
    val musicPaused: Boolean = false,
    val fanLowered: Boolean = false,
) {
    val remainingSeconds: Int get() = ((CapsuleRecorder.MAX_MS - elapsedMs + 999) / 1000).toInt().coerceAtLeast(0)
}

/**
 * 15초 목소리 캡슐. 화면에 들어오면 바로 녹음하고, 끝나면 누가 남겼는지 고른다.
 * 녹음하는 동안은 "도란아" 대기를 멈춰 마이크가 겹치지 않게 하고, 음악을 멈추고 송풍을 낮춰 목소리만 담기게 한다.
 */
class RecordViewModel(
    private val coordinator: TripCoordinator,
    private val recorder: CapsuleRecorder,
    private val store: CapsuleStore,
    preferences: AppPreferences,
    private val music: MusicPlayer,
    private val vehicle: VehicleStateSource,
) : ViewModel() {
    private val capsuleId = store.newId()
    private val _state = MutableStateFlow(
        RecordUiState(family = preferences.state.value.family + "다 같이", placeName = coordinator.state.value.lastPlaceName),
    )
    val state: StateFlow<RecordUiState> = _state.asStateFlow()
    private var ticker: Job? = null

    // 녹음 때문에 멈춘 음악만 다시 튼다. 원래 멈춰 있던 음악은 그대로 둔다
    private val musicWasPlaying = music.nowPlaying.value != null && !music.paused.value

    init {
        coordinator.pauseStandby()
        if (musicWasPlaying) music.pause()
        _state.update { it.copy(musicPaused = musicWasPlaying) }
        vehicle.lowerFan { _state.update { it.copy(fanLowered = true) } }
        if (recorder.start(store.file(capsuleId))) startTicker() else _state.update { it.copy(phase = RecordPhase.FAILED) }
    }

    private fun startTicker() {
        val startedAt = System.currentTimeMillis()
        ticker = viewModelScope.launch {
            while (isActive) {
                val elapsed = System.currentTimeMillis() - startedAt
                _state.update { it.copy(elapsedMs = elapsed, level = recorder.level()) }
                if (elapsed >= CapsuleRecorder.MAX_MS) {
                    finishRecording()
                    break
                }
                delay(TICK_MS)
            }
        }
    }

    /** "다 했어"를 누르거나 15초가 지나면 */
    fun finishRecording() {
        if (_state.value.phase != RecordPhase.RECORDING) return
        ticker?.cancel()
        recorder.stop()
        restoreCabin()
        _state.update {
            // 1초도 안 되는 녹음은 실수로 보고 버린다
            if (it.elapsedMs < MIN_MS || !store.has(capsuleId)) it.copy(phase = RecordPhase.FAILED, level = 0f)
            else it.copy(phase = RecordPhase.CHOOSE_SPEAKER, level = 0f)
        }
    }

    fun chooseSpeaker(name: String) {
        if (_state.value.phase != RecordPhase.CHOOSE_SPEAKER) return
        coordinator.resumeStandby()
        coordinator.saveCapsule(capsuleId, name, _state.value.elapsedMs.coerceAtMost(CapsuleRecorder.MAX_MS))
        _state.update { it.copy(phase = RecordPhase.DONE) }
    }

    fun cancel() {
        if (_state.value.phase == RecordPhase.DONE) return
        ticker?.cancel()
        recorder.stop()
        restoreCabin()
        store.delete(capsuleId)
        coordinator.resumeStandby()
        _state.update { it.copy(phase = RecordPhase.DONE) }
    }

    private var cabinRestored = false

    private fun restoreCabin() {
        if (cabinRestored) return
        cabinRestored = true
        vehicle.restoreFan()
        if (musicWasPlaying) music.resume()
    }

    override fun onCleared() {
        // 화면을 벗어나면 저장하지 않은 녹음은 지운다
        cancel()
    }

    companion object {
        private const val TICK_MS = 100L
        private const val MIN_MS = 1_000L

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as DoranApplication).container
                RecordViewModel(
                    container.tripCoordinator,
                    container.recorder,
                    container.capsules,
                    container.preferences,
                    container.musicPlayer,
                    container.vehicleStateSource,
                )
            }
        }
    }
}
