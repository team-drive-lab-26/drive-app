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

data class AlbumUiState(
    val loading: Boolean = true,
    val trips: List<TripItem> = emptyList(),
    /** 서버에 닿지 않아 기기에 있는 녹음만 보여 주는 중 */
    val offline: Boolean = false,
)

private val REGION_NAMES = mapOf("gangneung" to "강릉", "dummy" to "시연")
private val DAY = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)
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
                    ).joinToString(", "),
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
            _state.value = AlbumUiState(
                loading = false,
                trips = items.values.sortedByDescending { it.sortKey },
                offline = remote == null,
            )
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

private fun formatDay(millis: Long): String =
    if (millis == 0L) "날짜 모름" else Instant.ofEpochMilli(millis).atZone(SEOUL).format(DAY)
