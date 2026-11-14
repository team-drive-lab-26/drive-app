package com.doran.app.core.trip

import com.doran.app.core.data.NaviArrival
import com.doran.app.core.data.NaviState
import com.doran.app.core.model.Gear
import com.doran.app.core.model.VehicleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripEventDetectorTest {
    private val detector = TripEventDetector()
    private val noRoute = NaviState(isConnected = true)
    private val route = NaviState(isConnected = true, isRouteActive = true, destinationName = "강릉", routeDistanceKm = 220.0, etaMinutes = 150)

    private fun min(m: Int) = m * 60_000L
    private fun events(triggers: List<TripTrigger>) = triggers.filterIsInstance<TripTrigger.Event>().map { it.name }
    private fun park(door: Boolean = false) = VehicleState(gear = Gear.P, isAnyDoorOpen = door)
    private val drive = VehicleState(gear = Gear.D)

    @Test
    fun `경로를 잡고 출발하면 바로 출발 이벤트를 한 번 낸다`() {
        assertEquals(emptyList<String>(), events(detector.update(park(), route, 0)))
        assertEquals(listOf(TripEvent.TRIP_STARTED), events(detector.update(drive, route, min(1))))
        assertEquals(emptyList<String>(), events(detector.update(drive, route, min(2))))
    }

    @Test
    fun `경로 없이 출발하면 2분 기다린 뒤 출발 이벤트를 낸다`() {
        assertEquals(emptyList<String>(), events(detector.update(drive, noRoute, 0)))
        assertEquals(emptyList<String>(), events(detector.update(drive, noRoute, min(1))))
        assertEquals(listOf(TripEvent.TRIP_STARTED), events(detector.update(drive, noRoute, min(2))))
    }

    @Test
    fun `110분 연속 운전하면 휴식 이벤트를 한 번 낸다`() {
        detector.update(drive, route, 0)
        assertEquals(emptyList<String>(), events(detector.update(drive, route, min(109))))
        assertEquals(listOf(TripEvent.CONTINUOUS_DRIVE), events(detector.update(drive, route, min(110))))
        assertEquals(emptyList<String>(), events(detector.update(drive, route, min(140))))
        assertEquals(140, detector.context(min(140)).continuousDrivingMinutes)
    }

    @Test
    fun `15분 넘게 쉬면 연속 운전 시간을 새로 센다`() {
        detector.update(drive, route, 0)
        detector.update(park(), route, min(100))
        detector.update(drive, route, min(120))
        assertEquals(20, detector.context(min(120)).lastStopMinutes)
        assertEquals(0, detector.context(min(120)).continuousDrivingMinutes)
    }

    @Test
    fun `짧게 서면 연속 운전 시간이 이어진다`() {
        detector.update(drive, route, 0)
        detector.update(park(), route, min(100))
        detector.update(drive, route, min(105))
        assertEquals(listOf(TripEvent.CONTINUOUS_DRIVE), events(detector.update(drive, route, min(110))))
    }

    @Test
    fun `문을 열고 15분 넘게 쉬다 다시 타서 닫으면 재탑승 이벤트를 낸다`() {
        detector.update(drive, route, 0)
        detector.update(park(), route, min(10))
        detector.update(park(door = true), route, min(11))
        detector.update(park(), route, min(12))
        // 닫았지만 아직 15분이 안 됐다
        detector.update(park(door = true), route, min(20))
        assertEquals(listOf(TripEvent.REBOARDED), events(detector.update(park(), route, min(26))))
        assertEquals(16, detector.context(min(26)).lastStopMinutes)
        assertEquals(emptyList<String>(), events(detector.update(park(), route, min(27))))
    }

    @Test
    fun `문을 열지 않고 오래 서 있으면 재탑승이 아니다`() {
        detector.update(drive, route, 0)
        detector.update(park(), route, min(10))
        assertEquals(emptyList<String>(), events(detector.update(park(), route, min(40))))
    }

    @Test
    fun `도착 30분 전 이벤트는 목적지마다 한 번 낸다`() {
        detector.update(drive, route, 0)
        assertEquals(listOf(TripEvent.ARRIVAL_30M), events(detector.update(drive, route.copy(etaMinutes = 30), min(1))))
        assertEquals(emptyList<String>(), events(detector.update(drive, route.copy(etaMinutes = 20), min(2))))
        val next = route.copy(destinationName = "속초", etaMinutes = 25)
        assertEquals(listOf(TripEvent.ARRIVAL_30M), events(detector.update(drive, next, min(3))))
    }

    @Test
    fun `경유지에 도착해 P로 세우면 들른 장소로 기록한다`() {
        detector.update(drive, route, 0)
        detector.onArrival(NaviArrival("주문진 휴게소", isWaypoint = true), min(30))
        val triggers = detector.update(park(), route, min(31))
        assertTrue(triggers.contains(TripTrigger.Stopped("주문진 휴게소", isWaypoint = true)))
    }

    @Test
    fun `도착하고 한참 뒤에 세우면 그 장소로 기록하지 않는다`() {
        detector.update(drive, route, 0)
        detector.onArrival(NaviArrival("주문진 휴게소", isWaypoint = true), min(30))
        val triggers = detector.update(park(), route, min(45))
        assertTrue(triggers.none { it is TripTrigger.Stopped })
    }
}
