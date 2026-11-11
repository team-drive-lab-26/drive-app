package com.doran.app.core.trip

import com.doran.app.core.data.AgentCard
import com.doran.app.core.data.AgentReply
import com.doran.app.core.data.AgentRepository
import com.doran.app.core.data.NaviCommandResult
import com.doran.app.core.data.PleosNavi
import com.doran.app.core.data.VehicleStateSource
import com.doran.app.core.media.Capsule
import com.doran.app.core.media.CapsulePlayer
import com.doran.app.core.media.CapsuleStore
import com.doran.app.core.media.MusicPlayer
import com.doran.app.core.model.AheadStop
import com.doran.app.core.model.Gear
import com.doran.app.core.model.TapeKind
import com.doran.app.core.model.TapeMark
import com.doran.app.core.network.NavigationDto
import com.doran.app.core.network.VehicleDto
import com.doran.app.core.voice.DoranVoice
import com.doran.app.core.voice.ListenResult
import com.doran.app.core.voice.VoiceEngine
import com.doran.app.core.voice.WakeMatch
import com.doran.app.core.voice.WakeWord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    /** 호출어 대기 중. 이때 들은 말은 화면에 보이지 않고 서버로 보내지 않는다 */
    val isStandby: Boolean = false,
    /** 호출됐거나 제안에 대한 답을 듣는 중 */
    val isAwake: Boolean = false,
    /** 경유지 추가, 녹음 저장처럼 일이 끝난 시각. 바뀔 때마다 도란이 한 번 웃는다 */
    val cheerAt: Long = 0L,
    /** 정차 중 보여 줄 제안 카드. 주행으로 바뀌면 닫는다 */
    val cards: List<AgentCard> = emptyList(),
    /** 녹음 화면을 열어 달라는 요청 시각. 화면이 이 값이 바뀌면 녹음 화면으로 간다 */
    val recordRequestAt: Long = 0L,
    /** 마지막으로 도착한 곳. 녹음에 붙인다 */
    val lastPlaceName: String = "",
    /** 디버그 패널에서 보는 최근 기록 */
    val logs: List<TripLog> = emptyList(),
    /** 이번 여행 테이프. 출발, 들른 곳, 남긴 목소리 순서 */
    val tape: List<TapeMark> = emptyList(),
    /** 시연 촬영용 목적지와 남은 분. 내비가 연결되면 내비 값을 쓴다 */
    val demoDestination: String = "",
    val demoEtaMinutes: Int? = null,
    /** 앞길 표지판. 지금은 시연 촬영 때만 채운다. 내비가 경로 위 휴게소 거리를 주면 여기로 받는다 */
    val ahead: List<AheadStop> = emptyList(),
    /** 내비에 넣은 경유지 이름. 정차 화면 경로선이 이곳으로 휜다. 도착하면 비운다 */
    val detour: String = "",
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
    private val capsules: CapsuleStore,
    private val capsulePlayer: CapsulePlayer,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** 앱을 켤 때마다 새 여행. 앨범에서 "이번 여행"을 찾을 때 쓴다 */
    val sessionId = UUID.randomUUID().toString()

    // "한마디 남길래?"에 대한 답을 기다리는 중
    private var awaitingRecordAnswer = false
    private val detector = TripEventDetector()

    // 내비는 장소 ID를 모르므로, 우리가 추가한 경유지 이름으로 서버 장소 ID를 찾는다
    private val waypointIds = mutableMapOf<String, String>()
    private var pendingTrackId: String? = null
    private var started = false

    // 호출어 대기. 마이크 권한을 받으면 켜고, 녹음처럼 마이크를 따로 쓰는 동안은 멈춘다
    private var handsFree = false
    private var standbyPaused = false
    private var standbyJob: Job? = null

    private val _state = MutableStateFlow(TripConversationState())
    val state: StateFlow<TripConversationState> = _state.asStateFlow()

    fun start() {
        if (started) return
        started = true
        addMark(TapeKind.START, "출발")
        vehicle.start()
        navi.start()
        voice.start()

        scope.launch {
            combine(vehicle.state, navi.state) { v, n -> v to n }.collect { (v, n) ->
                if (v.isDriving && _state.value.cards.isNotEmpty()) dismissCards()
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
                if (tick % 2 == 0) navi.refresh()
                // 정차 화면 길가 표지판. 1분마다 앞으로 나올 휴게소 거리를 받아 온다
                if (tick++ % 2 == 0) refreshAhead()
                handle(detector.update(vehicle.state.value, navi.state.value, clock()))
            }
        }
    }

    // ---- 화면에서 부르는 동작 ----

    /** 버튼·캐릭터 터치, "도란아" 호출, 제안 뒤에 다음 한 문장을 듣는다 */
    fun listen() {
        if (_state.value.isThinking) return
        stopStandby()
        setCaption("듣고 있어")
        _state.update { it.copy(isAwake = true) }
        voice.listen { result ->
            _state.update { it.copy(isAwake = false) }
            when (result) {
                is ListenResult.Heard -> submitUtterance(result.text)
                ListenResult.NothingHeard -> {
                    setCaption("다시 말해 줘")
                    scheduleStandby()
                }
                ListenResult.Unavailable -> setCaption("음성 인식을 사용할 수 없어")
            }
        }
    }

    /** 주행 화면의 정지 아이콘. 말하던 것과 듣던 것을 멈추고 대기로 돌아간다 */
    fun stopTalking() {
        voice.stopSpeaking()
        cancelListening()
    }

    /** 디버그용. 화면 촬영 때 자막 한 줄을 바로 띄운다 */
    fun showDebugCaption(text: String) = setCaption(text)

    /** 디버그용. 경유지를 넣은 것처럼 경로선을 휘게 한다 */
    fun showDebugDetour(name: String) = _state.update { it.copy(detour = name, cards = emptyList()) }

    fun cancelListening() {
        voice.cancelListening()
        _state.update { it.copy(isAwake = false) }
        setCaption("")
        scheduleStandby()
    }

    /** 마이크 권한을 받은 뒤 부른다. 이후로는 "도란아"만 불러도 듣는다 */
    fun enableHandsFree() {
        if (handsFree) return
        handsFree = true
        log("호출어 대기 시작")
        scheduleStandby(delayMs = 0)
    }

    /** 설정에서 "도란아" 대기를 끄면 버튼으로만 듣는다 */
    fun disableHandsFree() {
        handsFree = false
        stopStandby()
        log("호출어 대기 끔")
    }

    /** 음성 캡슐 녹음처럼 마이크를 따로 쓰기 전에 부른다 */
    fun pauseStandby() {
        standbyPaused = true
        stopStandby()
    }

    fun resumeStandby() {
        standbyPaused = false
        scheduleStandby()
    }

    fun onMicPermissionDenied() = setCaption("마이크 권한이 필요해")

    /** 정차 화면에서 카드를 눌렀다. 장소는 경유지로, 곡은 재생으로 */
    fun chooseCard(card: AgentCard) {
        log("카드 선택: ${card.name}")
        if (card.isTrack) {
            // 곡 카드는 남겨 둬 다른 곡으로 바꿔 틀 수 있게 한다
            ask(utterance = "틀어 줘", selectedTrackId = card.trackId)
        } else {
            ask(utterance = "여기 가자", selectedPlaceId = card.placeId)
            dismissCards()
        }
    }

    /** 음악 화면에 들어오면 지금 어울리는 곡을 받아 온다 */
    fun requestMusic() {
        if (_state.value.cards.any { it.isTrack }) return
        ask(utterance = "노래 추천해 줘")
    }

    fun dismissCards() = _state.update { it.copy(cards = emptyList()) }

    fun submitUtterance(text: String) {
        log("발화: $text")
        if (awaitingRecordAnswer) {
            awaitingRecordAnswer = false
            val answer = text.trim()
            when {
                RECORD_NO.any { answer.contains(it) } -> {
                    say("알겠어.")
                    return
                }
                RECORD_YES.any { answer.contains(it) } -> {
                    requestRecord()
                    return
                }
            }
        }
        ask(utterance = text)
    }

    /** 정차 화면이나 "한마디 남길래?"에서 녹음 화면을 연다 */
    fun requestRecord() {
        stopStandby()
        setCaption("")
        _state.update { it.copy(recordRequestAt = clock()) }
    }

    /** 녹음 화면에서 남긴 사람을 고른 뒤 부른다. 파일은 이미 기기에 있다 */
    fun saveCapsule(id: String, speaker: String, durationMs: Long) {
        val place = _state.value.lastPlaceName
        capsules.save(Capsule(id, sessionId, speaker, place, clock(), durationMs))
        addMark(TapeKind.VOICE, speaker, id = id, seconds = (durationMs / 1000).toInt().coerceAtLeast(1))
        log("캡슐 저장: $speaker ${durationMs / 1000}초")
        cheer()
        say("저장했어. 앨범에서 다시 들을 수 있어.")
        if (!agent.isConfigured) return
        scope.launch {
            try {
                agent.recordTrip(sessionId, type = "capsule", id = id, name = speaker)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                log("캡슐 기록 실패")
            }
        }
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

    /**
     * 디버그용. 화면 촬영을 위해 지나온 여행을 테이프에 채운다. 시각은 지금 기준으로 거꾸로 잡는다.
     * 실제 기록과 섞이지 않게 기존 테이프를 바꾼다.
     */
    fun seedDemoTape() {
        val now = clock()
        fun ago(min: Int) = now - min * 60_000L
        _state.update {
            it.copy(
                tape = listOf(
                    TapeMark("demo-start", TapeKind.START, "서울시청 출발", ago(62)),
                    TapeMark("demo-voice-0", TapeKind.VOICE, "아빠", ago(61), seconds = 6),
                    TapeMark("demo-stop-1", TapeKind.STOP, "광장시장", ago(44)),
                    TapeMark("demo-voice-1", TapeKind.VOICE, "아이", ago(30), seconds = 12),
                    TapeMark("demo-voice-2", TapeKind.VOICE, "엄마", ago(28), seconds = 8),
                ),
                lastPlaceName = "광장시장",
                demoDestination = "강릉역",
                demoEtaMinutes = 158,
                ahead = listOf(AheadStop("하남드림 휴게소", 12.0), AheadStop("횡성휴게소", 68.0)),
            )
        }
    }

    private fun refreshAhead() {
        if (!agent.isConfigured || !navi.state.value.isRouteActive) return
        scope.launch {
            try {
                val stops = agent.ahead(navigationDto())
                if (stops.isNotEmpty()) _state.update { it.copy(ahead = stops) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                log("앞길 휴게소 조회 실패")
            }
        }
    }

    private fun addMark(kind: TapeKind, label: String, id: String = UUID.randomUUID().toString(), seconds: Int = 0) {
        _state.update { it.copy(tape = it.tape + TapeMark(id, kind, label, clock(), seconds)) }
    }

    /** 디버그용. 에뮬레이터 내비에 강릉 목적지를 잡는다 */
    fun startDemoRoute() {
        val result = navi.requestRoute("강릉역", 37.7643, 128.8996, "강원특별자치도 강릉시 용지로 176")
        log("경로 요청: 강릉역 → ${result::class.simpleName}")
    }

    // ---- 호출어 대기 ----

    private fun scheduleStandby(delayMs: Long = STANDBY_RESTART_MS) {
        if (!handsFree || standbyPaused) return
        standbyJob?.cancel()
        standbyJob = scope.launch {
            delay(delayMs)
            startStandby()
        }
    }

    private fun startStandby() {
        val s = _state.value
        // 말하는 중, 생각하는 중, 이미 듣는 중이면 끝난 뒤 다시 부른다
        if (!handsFree || standbyPaused || s.isThinking || s.isAwake || s.isStandby || voice.state.value.isSpeaking) return
        // 음성 엔진은 앱을 켠 뒤 비동기로 준비되므로, 준비될 때까지 기다렸다가 켠다
        if (voice.state.value.listener == VoiceEngine.NONE) {
            scheduleStandby(STANDBY_WAIT_ENGINE_MS)
            return
        }
        _state.update { it.copy(isStandby = true) }
        voice.listen { result ->
            _state.update { it.copy(isStandby = false) }
            when (result) {
                is ListenResult.Heard -> onStandbyHeard(result.text)
                ListenResult.NothingHeard -> scheduleStandby()
                ListenResult.Unavailable -> scheduleStandby(STANDBY_WAIT_ENGINE_MS)
            }
        }
    }

    private fun onStandbyHeard(text: String) {
        when (val match = WakeWord.match(text)) {
            WakeMatch.None -> scheduleStandby()
            WakeMatch.CallOnly -> {
                log("호출")
                voice.chime()
                say("응?", onDone = ::listen)
            }
            is WakeMatch.Command -> submitUtterance(match.text)
        }
    }

    private fun stopStandby() {
        standbyJob?.cancel()
        standbyJob = null
        if (_state.value.isStandby) {
            voice.cancelListening()
            _state.update { it.copy(isStandby = false) }
        }
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
        _state.update { it.copy(lastPlaceName = stop.placeName) }
        addMark(TapeKind.STOP, stop.placeName)
        if (_state.value.detour == stop.placeName) _state.update { it.copy(detour = "") }
        // 들른 곳에서 목소리를 남기자고 먼저 묻는다. 답은 호출어 없이 바로 듣는다
        awaitingRecordAnswer = true
        say("${stop.placeName}에 도착했어. 여기서 한마디 남길래?", onDone = ::listen)
        if (!agent.isConfigured) return
        scope.launch {
            try {
                agent.recordTrip(sessionId, type = "stop", id = id, name = stop.placeName)
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
        selectedPlaceId: String? = null,
        selectedTrackId: String? = null,
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
                    selectedPlaceId = selectedPlaceId,
                    selectedTrackId = selectedTrackId,
                )
                log("응답(${reply.status}): ${reply.caption.ifBlank { reply.speech }.take(40)}")
                when {
                    // 사용자가 말하는 중에는 이벤트 제안이 끼어들지 않는다
                    !isUtterance && _state.value.isAwake -> log("듣는 중이라 제안 건너뜀")
                    // 이벤트에 제안이 없으면 조용히 넘어간다
                    isUtterance || reply.speech.isNotBlank() -> deliver(reply, isUtterance)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                log("서버 연결 실패")
                if (isUtterance) say("AI 서버에 연결할 수 없어")
            } finally {
                if (isUtterance) _state.update { it.copy(isThinking = false) }
                // 말할 게 없었거나 말하기에 실패했으면 바로 대기로 돌아간다. 말하는 중이면 끝난 뒤 돌아간다
                scheduleStandby()
            }
        }
    }

    private fun deliver(reply: AgentReply, isUtterance: Boolean) {
        var spoken = reply.speech
        when {
            reply.cards.isNotEmpty() && !vehicle.state.value.isDriving -> _state.update { it.copy(cards = reply.cards) }
            // 곡을 틀었을 때는 곡 카드를 남긴다
            reply.requestedTrackId != null -> Unit
            // "아니", 경유지 추가처럼 새 카드 없이 답하면 지난 카드를 닫는다
            isUtterance -> dismissCards()
        }
        reply.waypoint?.let { waypoint ->
            waypointIds[waypoint.name] = waypoint.id
            navi.addWaypoint(waypoint) { result ->
                log("경유지 요청: ${waypoint.name} → ${result::class.simpleName}")
                when (result) {
                    NaviCommandResult.Submitted -> {
                        _state.update { it.copy(detour = waypoint.name) }
                        cheer()
                    }
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
        val capsuleId = reply.requestedCapsuleId
        if (capsuleId != null && !capsules.has(capsuleId)) spoken = "그 녹음은 이 기기에 없어."
        val playCapsule = capsuleId?.takeIf(capsules::has)
        val caption = reply.caption.ifBlank { spoken }.ifBlank { if (isUtterance) "다시 말해 줘" else "" }
        // 도란이 먼저 물어본 제안은 호출어 없이 바로 답을 듣는다("응", "아니")
        val awaitAnswer = reply.status == "needs_confirmation" && pendingTrackId == null
        when {
            awaitAnswer -> say(spoken, caption, onDone = ::listen)
            // 안내가 끝난 뒤 목소리를 틀고, 다 들으면 대기로 돌아간다
            playCapsule != null -> say(spoken, caption) { capsulePlayer.play(playCapsule, onDone = ::scheduleStandbyNow) }
            else -> say(spoken, caption)
        }
    }

    private fun say(text: String, caption: String = text, onDone: () -> Unit = ::scheduleStandbyNow) {
        // 도란의 목소리를 호출어로 다시 받아 적지 않게, 말하는 동안은 마이크를 끈다
        stopStandby()
        if (caption.isNotBlank()) setCaption(caption)
        val finish = {
            playPendingTrack()
            onDone()
        }
        if (!voice.speak(text, onDone = finish)) finish()
    }

    private fun scheduleStandbyNow() = scheduleStandby()

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

    private fun cheer() = _state.update { it.copy(cheerAt = clock()) }

    private fun log(text: String) {
        _state.update { it.copy(logs = (listOf(TripLog(clock(), text)) + it.logs).take(MAX_LOGS)) }
    }

    private companion object {
        const val TICK_MS = 30_000L
        // 인식기가 바로 오류를 내도 쉬지 않고 다시 켜지 않게 잠깐 쉰다
        const val STANDBY_RESTART_MS = 400L
        const val STANDBY_WAIT_ENGINE_MS = 2_000L
        val RECORD_YES = listOf("응", "네", "그래", "좋아", "남길", "할래", "녹음")
        val RECORD_NO = listOf("아니", "괜찮아", "됐어", "나중에", "싫어", "안 남", "안 할")
        const val MAX_LOGS = 30
        val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
