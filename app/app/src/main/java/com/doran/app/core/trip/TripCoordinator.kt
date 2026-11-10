package com.doran.app.core.trip

import com.doran.app.core.data.AgentReply
import com.doran.app.core.data.AgentRepository
import com.doran.app.core.data.NaviCommandResult
import com.doran.app.core.data.PleosNavi
import com.doran.app.core.data.VehicleStateSource
import com.doran.app.core.media.MusicPlayer
import com.doran.app.core.model.Gear
import com.doran.app.core.network.NavigationDto
import com.doran.app.core.network.VehicleDto
import com.doran.app.core.voice.DoranVoice
import com.doran.app.core.voice.ListenResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

data class TripLog(val atMillis: Long, val text: String)

data class TripConversationState(
    val caption: String = "",
    val isThinking: Boolean = false,
    /** 디버그 패널에서 보는 최근 기록 */
    val logs: List<TripLog> = emptyList(),
)

/**
 * 여행 한 번의 흐름을 앱 범위에서 맡는다. 화면이 바뀌거나 다시 만들어져도 이어진다.
 * 차량·내비 값으로 이벤트를 판단해 AI 서버에 보내고, 답을 말하고, 경유지·음악 요청을 처리한다.
 */
class TripCoordinator(
    private val scope: CoroutineScope,
    private val agent: AgentRepository,
    private val vehicle: VehicleStateSource,
    private val navi: PleosNavi,
    private val voice: DoranVoice,
    private val music: MusicPlayer,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val sessionId = UUID.randomUUID().toString()
    private val detector = TripEventDetector()

    // 내비는 장소 ID를 모르므로, 우리가 추가한 경유지 이름으로 서버 장소 ID를 찾는다
    private val waypointIds = mutableMapOf<String, String>()
    private var pendingTrackId: String? = null
    private var started = false

    private val _state = MutableStateFlow(TripConversationState())
    val state: StateFlow<TripConversationState> = _state.asStateFlow()

    fun start() {
        if (started) return
        started = true
        vehicle.start()
        navi.start()
        voice.start()

        scope.launch {
            combine(vehicle.state, navi.state) { v, n -> v to n }.collect { (v, n) ->
                handle(detector.update(v, n, clock()))
            }
        }
        scope.launch {
            navi.arrivals.collect { arrival ->
                detector.onArrival(arrival, clock())
                log("내비 도착: ${arrival.name}${if (arrival.isWaypoint) " (경유지)" else " (목적지)"}")
            }
        }
        scope.launch {
            navi.errors.collect { code ->
                log("내비 오류: $code")
                say("내비에서 요청을 처리하지 못했어.")
            }
        }
        // 시간이 지나야 생기는 이벤트(연속 운전, 오래 정차)를 놓치지 않게 주기적으로 다시 판단한다
        scope.launch {
            var tick = 0
            while (isActive) {
                delay(TICK_MS)
                if (tick++ % 2 == 0) navi.refresh()
                handle(detector.update(vehicle.state.value, navi.state.value, clock()))
            }
        }
    }

    // ---- 화면에서 부르는 동작 ----

    fun listen() {
        if (_state.value.isThinking) return
        setCaption("듣고 있어")
        voice.listen { result ->
            when (result) {
                is ListenResult.Heard -> submitUtterance(result.text)
                ListenResult.NothingHeard -> setCaption("다시 말해 줘")
                ListenResult.Unavailable -> setCaption("음성 인식을 사용할 수 없어")
            }
        }
    }

    fun cancelListening() {
        voice.cancelListening()
        setCaption("")
    }

    fun onMicPermissionDenied() = setCaption("마이크 권한이 필요해")

    fun submitUtterance(text: String) {
        log("발화: $text")
        ask(utterance = text)
    }

    fun overrideGear(gear: Gear) = vehicle.overrideGear(gear)

    fun overrideDoorOpen(open: Boolean) = vehicle.overrideDoorOpen(open)

    /** 디버그용. 서버 조건을 채운 값으로 이벤트를 바로 보낸다 */
    fun sendDemoEvent(event: String) {
        val v = vehicleDto().let {
            when (event) {
                TripEvent.CONTINUOUS_DRIVE -> it.copy(continuousDrivingMinutes = 130)
                TripEvent.REBOARDED -> it.copy(gear = Gear.P.name, doorsClosed = true, lastStopMinutes = 20)
                else -> it
            }
        }
        val n = navigationDto().let {
            when (event) {
                TripEvent.TRIP_STARTED -> it.copy(distanceFromHomeKm = 120.0)
                TripEvent.ARRIVAL_30M -> it.copy(etaMinutes = 25, arrivalLocalTime = clockAfter(25))
                else -> it
            }
        }
        log("이벤트(시연): $event")
        ask(event = event, vehicleOverride = v, navigationOverride = n)
    }

    /** 디버그용. 에뮬레이터 내비에 강릉 목적지를 잡는다 */
    fun startDemoRoute() {
        val result = navi.requestRoute("강릉역", 37.7643, 128.8996, "강원특별자치도 강릉시 용지로 176")
        log("경로 요청: 강릉역 → ${result::class.simpleName}")
    }

    // ---- 이벤트 처리 ----

    private fun handle(triggers: List<TripTrigger>) {
        triggers.forEach { trigger ->
            when (trigger) {
                is TripTrigger.Event -> {
                    log("이벤트: ${trigger.name}")
                    ask(event = trigger.name)
                }
                is TripTrigger.Stopped -> recordStop(trigger)
            }
        }
    }

    private fun recordStop(stop: TripTrigger.Stopped) {
        val id = waypointIds[stop.placeName] ?: "navi-${stop.placeName.take(100)}"
        log("정차 기록: ${stop.placeName}")
        if (!agent.isConfigured) return
        scope.launch {
            try {
                agent.recordTrip(sessionId, type = "stop", id = id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                log("정차 기록 실패")
            }
        }
    }

    private fun ask(
        utterance: String? = null,
        event: String? = null,
        vehicleOverride: VehicleDto? = null,
        navigationOverride: NavigationDto? = null,
    ) {
        val isUtterance = utterance != null
        if (!agent.isConfigured) {
            if (isUtterance) say("AI 서버 설정이 필요해")
            return
        }
        scope.launch {
            if (isUtterance) _state.update { it.copy(isThinking = true, caption = "생각하고 있어") }
            try {
                val reply = agent.send(
                    sessionId = sessionId,
                    vehicle = vehicleOverride ?: vehicleDto(),
                    utterance = utterance,
                    event = event,
                    navigation = navigationOverride ?: navigationDto(),
                )
                log("응답(${reply.status}): ${reply.caption.ifBlank { reply.speech }.take(40)}")
                // 이벤트에 제안이 없으면 조용히 넘어간다
                if (isUtterance || reply.speech.isNotBlank()) deliver(reply, isUtterance)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                log("서버 연결 실패")
                if (isUtterance) say("AI 서버에 연결할 수 없어")
            } finally {
                if (isUtterance) _state.update { it.copy(isThinking = false) }
            }
        }
    }

    private fun deliver(reply: AgentReply, isUtterance: Boolean) {
        var spoken = reply.speech
        reply.waypoint?.let { waypoint ->
            waypointIds[waypoint.name] = waypoint.id
            navi.addWaypoint(waypoint) { result ->
                log("경유지 요청: ${waypoint.name} → ${result::class.simpleName}")
                when (result) {
                    NaviCommandResult.Submitted -> Unit
                    NaviCommandResult.NotConnected -> say("내비 연결을 확인해 줘.")
                    NaviCommandResult.Failed -> say("경유지를 추가하지 못했어.")
                }
            }
        }
        reply.requestedTrackId?.let { trackId ->
            if (music.canPlay(trackId)) {
                // 안내 음성과 겹치지 않게 말이 끝난 뒤 튼다
                pendingTrackId = trackId
            } else {
                spoken = "이 곡은 아직 들을 수 없어."
            }
        }
        if (reply.requestedCapsuleId != null) spoken = "녹음 재생 연결이 필요해."
        val caption = reply.caption.ifBlank { spoken }.ifBlank { if (isUtterance) "다시 말해 줘" else "" }
        say(spoken, caption)
    }

    private fun say(text: String, caption: String = text) {
        if (caption.isNotBlank()) setCaption(caption)
        if (!voice.speak(text, onDone = ::playPendingTrack)) playPendingTrack()
    }

    private fun playPendingTrack() {
        val trackId = pendingTrackId ?: return
        pendingTrackId = null
        music.play(trackId)
    }

    // ---- 서버에 보내는 맥락 ----

    private fun vehicleDto(): VehicleDto {
        val state = vehicle.state.value
        val context = detector.context(clock())
        return VehicleDto(
            gear = state.gear.name,
            doorsClosed = !state.isAnyDoorOpen,
            lastStopMinutes = context.lastStopMinutes,
            continuousDrivingMinutes = context.continuousDrivingMinutes,
        )
    }

    private fun navigationDto(): NavigationDto {
        val state = navi.state.value
        val context = detector.context(clock())
        val eta = state.etaMinutes.takeIf { state.isRouteActive }
        return NavigationDto(
            // 집 위치를 아직 받지 않아, 출발할 때 잡은 경로 거리로 대신한다
            distanceFromHomeKm = state.routeDistanceKm,
            etaMinutes = eta,
            arrivalLocalTime = eta?.let(::clockAfter),
            minutesSinceDeparture = context.minutesSinceDeparture,
        )
    }

    private fun clockAfter(minutes: Int): String = LocalTime.now().plusMinutes(minutes.toLong()).format(HH_MM)

    private fun setCaption(text: String) = _state.update { it.copy(caption = text) }

    private fun log(text: String) {
        _state.update { it.copy(logs = (listOf(TripLog(clock(), text)) + it.logs).take(MAX_LOGS)) }
    }

    private companion object {
        const val TICK_MS = 30_000L
        const val MAX_LOGS = 30
        val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
