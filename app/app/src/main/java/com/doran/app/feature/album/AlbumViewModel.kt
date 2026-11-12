package com.doran.app.feature.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.doran.app.DoranApplication
import com.doran.app.core.data.AgentRepository
import com.doran.app.core.media.CapsuleStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

data class TripItem(
    val sessionId: String,
    val title: String,
    val detail: String,
    val isCurrent: Boolean,
    val sortKey: Long,
)

/** 고른 여행의 들른 곳 한 줄. [voices]는 거기서 목소리를 남긴 사람 */
data class TripStop(val name: String, val time: String, val voices: List<String>)

/** 고른 여행 미리보기. 들른 순서대로 */
data class TripPreview(val sessionId: String, val loading: Boolean, val stops: List<TripStop> = emptyList(), val voiceCount: Int = 0)

data class AlbumUiState(
    val loading: Boolean = true,
    val trips: List<TripItem> = emptyList(),
    /** 서버에 닿지 않아 기기에 있는 녹음만 보여 주는 중 */
    val offline: Boolean = false,
    val selectedId: String? = null,
    val preview: TripPreview? = null,
) {
    val selected: TripItem? get() = trips.firstOrNull { it.sessionId == selectedId }
}

private val REGION_NAMES = mapOf("gangneung" to "강릉", "dummy" to "시연")
private val DAY = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)
private val TIME = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)
private val SEOUL = ZoneId.of("Asia/Seoul")

/** 여행 목록. 서버 기록을 먼저 보고, 서버에 없는 여행이라도 기기에 녹음이 있으면 같이 보여 준다 */
class AlbumViewModel(
    private val agent: AgentRepository,
    private val capsules: CapsuleStore,
    private val currentSessionId: String,
) : ViewModel() {
    private val _state = MutableStateFlow(AlbumUiState())
    val state: StateFlow<AlbumUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val local = capsules.capsules.value.groupBy { it.sessionId }
            val remote = try {
                if (agent.isConfigured) agent.trips() else null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            val items = mutableMapOf<String, TripItem>()
            remote?.forEach { trip ->
                val started = parseMillis(trip.startedAt)
                val voices = local[trip.sessionId]?.size ?: 0
                val region = REGION_NAMES[trip.region] ?: "여행"
                items[trip.sessionId] = TripItem(
                    sessionId = trip.sessionId,
                    title = formatDay(started),
                    detail = listOfNotNull(
                        "$region 여행",
                        "기록 ${trip.eventCount}개",
                        if (voices > 0) "목소리 ${voices}개" else null,
                    ).joinToString(" · "),
                    isCurrent = trip.sessionId == currentSessionId,
                    sortKey = started,
                )
            }
            local.forEach { (sessionId, list) ->
                if (sessionId in items) return@forEach
                val started = list.minOf { it.createdAt }
                items[sessionId] = TripItem(
                    sessionId = sessionId,
                    title = formatDay(started),
                    detail = "목소리 ${list.size}개",
                    isCurrent = sessionId == currentSessionId,
                    sortKey = started,
                )
            }
            val trips = items.values.sortedByDescending { it.sortKey }
            _state.value = AlbumUiState(
                loading = false,
                trips = trips,
                offline = remote == null,
            )
            trips.firstOrNull()?.let { select(it.sessionId) }
        }
    }

    /** 길가 표지판을 고르면 그 여행에서 들른 곳과 남긴 목소리를 불러와 옆 패널에 보인다 */
    fun select(sessionId: String) {
        if (_state.value.preview?.sessionId == sessionId && _state.value.preview?.loading == false) {
            _state.value = _state.value.copy(selectedId = sessionId)
            return
        }
        _state.value = _state.value.copy(selectedId = sessionId, preview = TripPreview(sessionId, loading = true))
        viewModelScope.launch {
            val localVoices = capsules.capsules.value.filter { it.sessionId == sessionId }
            val events = try {
                if (agent.isConfigured && !_state.value.offline) agent.tripEvents(sessionId).events else emptyList()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            val stops = mutableListOf<TripStop>()
            events.forEach { event ->
                when (event.type) {
                    "stop" -> stops += TripStop(event.name.ifBlank { "이름 모를 곳" }, formatTime(parseMillis(event.recordedAt)), emptyList())
                    "capsule" -> if (stops.isNotEmpty()) {
                        val last = stops.removeAt(stops.lastIndex)
                        stops += last.copy(voices = last.voices + event.name.ifBlank { "가족" })
                    }
                }
            }
            val voiceCount = maxOf(events.count { it.type == "capsule" }, localVoices.size)
            if (_state.value.selectedId == sessionId) {
                _state.value = _state.value.copy(preview = TripPreview(sessionId, loading = false, stops = stops, voiceCount = voiceCount))
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as DoranApplication).container
                AlbumViewModel(container.agentRepository, container.capsules, container.tripCoordinator.sessionId)
            }
        }
    }
}

// SQLite는 시간대 없이 돌려줄 때가 있다. 서버는 UTC로 저장하므로 시간대가 없으면 UTC로 읽는다
internal fun parseMillis(value: String): Long =
    runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        .recoverCatching { LocalDateTime.parse(value).toInstant(ZoneOffset.UTC).toEpochMilli() }
        .getOrDefault(0L)

private fun formatTime(millis: Long): String =
    if (millis == 0L) "" else Instant.ofEpochMilli(millis).atZone(SEOUL).format(TIME)

private fun formatDay(millis: Long): String =
    if (millis == 0L) "날짜 모름" else Instant.ofEpochMilli(millis).atZone(SEOUL).format(DAY)
