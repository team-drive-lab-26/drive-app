package com.doran.app.feature.albummap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.doran.app.DoranApplication
import com.doran.app.core.data.AgentRepository
import com.doran.app.core.media.CapsulePlayer
import com.doran.app.core.media.CapsuleStore
import com.doran.app.feature.album.parseMillis
import com.doran.app.navigation.Route
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Voice(val capsuleId: String, val speaker: String, val seconds: Int, val available: Boolean)

/** 지도에 꽂는 핀 하나. 들른 곳과 거기서 남긴 목소리 */
data class MapPin(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val time: String,
    val voices: List<Voice>,
)

data class AlbumMapUiState(
    val loading: Boolean = true,
    val title: String = "",
    val route: List<Pair<Double, Double>> = emptyList(),
    val pins: List<MapPin> = emptyList(),
    val selectedId: String? = null,
    val message: String = "",
) {
    val selected: MapPin? get() = pins.firstOrNull { it.id == selectedId }
}

private val TIME = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)
private val DAY = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)
private val SEOUL = ZoneId.of("Asia/Seoul")

/**
 * 여행 하나의 지도 앨범. 서버 기록에서 좌표가 있는 정차를 핀으로 만들고,
 * 그 뒤에 남긴 목소리 캡슐을 해당 핀에 붙인다. 녹음 파일은 기기에서 찾는다.
 */
class AlbumMapViewModel(
    savedState: SavedStateHandle,
    private val agent: AgentRepository,
    private val capsules: CapsuleStore,
    private val player: CapsulePlayer,
) : ViewModel() {
    private val sessionId = savedState.toRoute<Route.AlbumMap>().sessionId
    private val _state = MutableStateFlow(AlbumMapUiState())
    val state: StateFlow<AlbumMapUiState> = _state.asStateFlow()
    val playingId: StateFlow<String?> = player.playingId

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val local = capsules.capsules.value.filter { it.sessionId == sessionId }.associateBy { it.id }
            val trip = try {
                if (agent.isConfigured) agent.tripEvents(sessionId) else null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            val pins = mutableListOf<MapPin>()
            val waiting = mutableListOf<Voice>()
            val seen = mutableSetOf<String>()
            var firstMillis = 0L
            trip?.events.orEmpty().forEach { event ->
                val millis = parseMillis(event.recordedAt)
                if (firstMillis == 0L) firstMillis = millis
                when (event.type) {
                    "stop" -> {
                        val lat = event.latitude
                        val lon = event.longitude
                        if (lat != null && lon != null) {
                            pins += MapPin(event.id + "#" + pins.size, event.name.ifBlank { "들른 곳" }, lat, lon, formatTime(millis), waiting.toList())
                            waiting.clear()
                        }
                    }
                    "capsule" -> {
                        seen += event.id
                        val meta = local[event.id]
                        val voice = Voice(
                            capsuleId = event.id,
                            speaker = meta?.speaker ?: event.name.ifBlank { "가족" },
                            seconds = ((meta?.durationMs ?: 0L) / 1000).toInt(),
                            available = capsules.has(event.id),
                        )
                        if (pins.isEmpty()) waiting += voice else pins[pins.lastIndex] = pins.last().let { it.copy(voices = it.voices + voice) }
                    }
                }
            }
            // 서버에 닿지 않았을 때 남긴 녹음도 마지막 핀에 붙인다
            local.values.filter { it.id !in seen }.forEach { meta ->
                val voice = Voice(meta.id, meta.speaker, (meta.durationMs / 1000).toInt(), capsules.has(meta.id))
                if (pins.isEmpty()) waiting += voice else pins[pins.lastIndex] = pins.last().let { it.copy(voices = it.voices + voice) }
                if (firstMillis == 0L) firstMillis = meta.createdAt
            }
            if (waiting.isNotEmpty() && pins.isNotEmpty()) pins[0] = pins[0].copy(voices = waiting + pins[0].voices)

            val route = trip?.route.orEmpty().mapNotNull { point -> if (point.size >= 2) point[0] to point[1] else null }
            _state.value = AlbumMapUiState(
                loading = false,
                title = if (firstMillis == 0L) "여행 지도" else "${Instant.ofEpochMilli(firstMillis).atZone(SEOUL).format(DAY)} 여행",
                route = route,
                pins = pins,
                selectedId = pins.lastOrNull { it.voices.isNotEmpty() }?.id ?: pins.lastOrNull()?.id,
                message = when {
                    trip == null && local.isEmpty() -> "서버에 닿지 않아 기록을 불러오지 못했어."
                    pins.isEmpty() -> "아직 지도에 찍을 정차 기록이 없어."
                    else -> ""
                },
            )
        }
    }

    fun select(id: String) = _state.update { it.copy(selectedId = id) }

    fun toggle(voice: Voice) {
        if (voice.available) player.toggle(voice.capsuleId)
    }

    override fun onCleared() {
        player.stop()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as DoranApplication).container
                AlbumMapViewModel(createSavedStateHandle(), container.agentRepository, container.capsules, container.capsulePlayer)
            }
        }
    }
}

private fun formatTime(millis: Long): String =
    if (millis == 0L) "" else Instant.ofEpochMilli(millis).atZone(SEOUL).format(TIME)
