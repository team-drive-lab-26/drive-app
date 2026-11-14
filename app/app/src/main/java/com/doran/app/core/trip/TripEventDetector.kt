package com.doran.app.core.trip

import com.doran.app.core.data.NaviArrival
import com.doran.app.core.data.NaviState
import com.doran.app.core.model.Gear
import com.doran.app.core.model.VehicleState

/** 서버 `/v1/agent/respond`에 보내는 차량 이벤트 이름. docs/ai 계약과 같다 */
object TripEvent {
    const val TRIP_STARTED = "trip_started"
    const val ARRIVAL_30M = "arrival_30m"
    const val REBOARDED = "reboarded"
    const val CONTINUOUS_DRIVE = "continuous_drive"
}

sealed interface TripTrigger {
    /** 서버에 보낼 차량 이벤트 */
    data class Event(val name: String) : TripTrigger

    /** 내비 도착 뒤 P로 세웠다. 실제로 들른 장소다 */
    data class Stopped(val placeName: String, val isWaypoint: Boolean) : TripTrigger
}

/** 매 요청에 함께 보내는 주행 맥락 */
data class TripContext(
    val lastStopMinutes: Int?,
    val continuousDrivingMinutes: Int?,
    val minutesSinceDeparture: Int?,
)

/**
 * 기어, 문, 내비 값으로 여행 이벤트를 판단한다. 시간은 밖에서 넣어 테스트할 수 있게 한다.
 * 같은 이벤트를 반복해서 보내지 않도록 이벤트마다 한 번만 낸다. 빈도 제한은 서버가 한 번 더 건다.
 */
class TripEventDetector {
    private var firstDriveAt: Long? = null
    private var tripStartedSent = false
    private var drivingSince: Long? = null
    private var continuousSent = false
    private var parkedSince: Long? = null
    private var lastStopMinutes: Int? = null
    private var doorOpenedDuringStop = false
    private var reboardedSent = false
    private var arrivalSentFor: String? = null
    private var lastArrival: Pair<NaviArrival, Long>? = null

    fun onArrival(arrival: NaviArrival, now: Long) {
        lastArrival = arrival to now
    }

    fun update(vehicle: VehicleState, navi: NaviState, now: Long): List<TripTrigger> {
        val triggers = mutableListOf<TripTrigger>()
        val parked = vehicle.gear == Gear.P

        if (parked) {
            if (parkedSince == null) {
                parkedSince = now
                doorOpenedDuringStop = false
                reboardedSent = false
                lastArrival?.let { (arrival, at) ->
                    if (now - at <= ARRIVAL_TO_PARK_MS) triggers += TripTrigger.Stopped(arrival.name, arrival.isWaypoint)
                }
                lastArrival = null
            }
            val stopMinutes = minutesBetween(parkedSince!!, now)
            if (vehicle.isAnyDoorOpen) doorOpenedDuringStop = true
            // 문을 열고 내렸다가 다시 타서 문을 닫은 순간이다
            if (doorOpenedDuringStop && !vehicle.isAnyDoorOpen && !reboardedSent && stopMinutes > REBOARD_MIN_STOP_MINUTES) {
                lastStopMinutes = stopMinutes
                reboardedSent = true
                triggers += TripTrigger.Event(TripEvent.REBOARDED)
            }
        } else {
            parkedSince?.let { since ->
                val stopMinutes = minutesBetween(since, now)
                lastStopMinutes = stopMinutes
                // 충분히 쉬었으면 연속 운전 시간을 새로 센다
                if (stopMinutes >= REST_RESET_MINUTES) {
                    drivingSince = null
                    continuousSent = false
                }
            }
            parkedSince = null
            if (firstDriveAt == null) firstDriveAt = now
            if (drivingSince == null) drivingSince = now

            val continuous = minutesBetween(drivingSince!!, now)
            if (!continuousSent && continuous >= CONTINUOUS_DRIVE_MINUTES) {
                continuousSent = true
                triggers += TripTrigger.Event(TripEvent.CONTINUOUS_DRIVE)
            }

            val eta = navi.etaMinutes
            if (navi.isRouteActive && eta != null && eta in 1..ARRIVAL_SOON_MINUTES && arrivalSentFor != navi.destinationName) {
                arrivalSentFor = navi.destinationName
                triggers += TripTrigger.Event(TripEvent.ARRIVAL_30M)
            }
        }

        // 경로를 잡고 출발하면 경로 거리로 보내고, 경로 없이 출발했으면 잠시 기다린 뒤 보낸다
        val departedAt = firstDriveAt
        if (!tripStartedSent && departedAt != null) {
            val routeReady = navi.isRouteActive && navi.routeDistanceKm != null
            if (routeReady || now - departedAt >= TRIP_START_ROUTE_WAIT_MS) {
                tripStartedSent = true
                triggers.add(0, TripTrigger.Event(TripEvent.TRIP_STARTED))
            }
        }
        return triggers
    }

    fun context(now: Long): TripContext {
        val parked = parkedSince
        return TripContext(
            lastStopMinutes = if (parked != null) minutesBetween(parked, now) else lastStopMinutes,
            continuousDrivingMinutes = drivingSince?.takeIf { parked == null }?.let { minutesBetween(it, now) },
            minutesSinceDeparture = firstDriveAt?.let { minutesBetween(it, now) },
        )
    }

    private fun minutesBetween(from: Long, to: Long): Int = ((to - from) / 60_000L).toInt()

    companion object {
        // 서버 판단 기준(ai/doran_ai/agent.py)에 맞춘다
        const val REBOARD_MIN_STOP_MINUTES = 15
        const val CONTINUOUS_DRIVE_MINUTES = 110
        const val ARRIVAL_SOON_MINUTES = 30
        const val REST_RESET_MINUTES = 15
        const val TRIP_START_ROUTE_WAIT_MS = 2 * 60_000L
        const val ARRIVAL_TO_PARK_MS = 10 * 60_000L
    }
}
