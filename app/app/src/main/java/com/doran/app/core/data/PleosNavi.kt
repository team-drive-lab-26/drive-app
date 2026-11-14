package com.doran.app.core.data

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class NaviState(
    val isConnected: Boolean = false,
    val isRouteActive: Boolean = false,
    val destinationName: String = "",
    val routeDistanceKm: Double? = null,
    val etaMinutes: Int? = null,
)

/** 내비가 알려 준 도착. 경유지인지 최종 목적지인지 구분한다 */
data class NaviArrival(val name: String, val isWaypoint: Boolean)

sealed interface NaviCommandResult {
    data object Submitted : NaviCommandResult
    data object NotConnected : NaviCommandResult
    data object Failed : NaviCommandResult
}

class PleosNavi(context: Context) {
    private val helper = NaviHelper(context.applicationContext)
    private val _state = MutableStateFlow(NaviState())
    val state: StateFlow<NaviState> = _state.asStateFlow()
    private val _arrivals = MutableSharedFlow<NaviArrival>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val arrivals: SharedFlow<NaviArrival> = _arrivals.asSharedFlow()
    private val _errors = MutableSharedFlow<NaviErrorCode>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val errors: SharedFlow<NaviErrorCode> = _errors.asSharedFlow()

    private var connecting = false
    // 연결되면 실행할 요청과, 연결에 실패하면 알릴 곳
    private val pending = mutableListOf<Pair<() -> Unit, () -> Unit>>()

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(info: RouteStartInfo) {
            _state.update {
                it.copy(
                    isRouteActive = true,
                    destinationName = info.name,
                    routeDistanceKm = info.distance / 1000.0,
                    etaMinutes = info.duration.secondsToMinutes(),
                )
            }
        }

        override fun onRouteEnded() = clearRoute()
        override fun onRouteCancelled() = clearRoute()

        override fun onDrivingInfo(info: DrivingInfo) {
            _state.update { it.copy(isRouteActive = true, etaMinutes = info.destination.duration.secondsToMinutes()) }
        }

        override fun onDestinationInfo(info: DestinationInfo) {
            // 경로가 없을 때도 빈 목적지 정보가 온다
            if (info.name.isBlank() || info.remainDistance <= 0) {
                clearRoute()
                return
            }
            _state.update {
                it.copy(
                    isRouteActive = true,
                    destinationName = info.name,
                    etaMinutes = info.remainTime.secondsToMinutes(),
                )
            }
        }

        override fun onWayPointArrived(info: WaypointArrivedInfo) {
            _arrivals.tryEmit(NaviArrival(info.name, isWaypoint = true))
        }

        override fun onDestinationArrived(info: DestinationArrivedInfo) {
            _arrivals.tryEmit(NaviArrival(info.name, isWaypoint = false))
            clearRoute()
        }

        override fun onError(errorCode: NaviErrorCode) {
            _errors.tryEmit(errorCode)
        }
    }

    fun start() {
        if (_state.value.isConnected || connecting) return
        connecting = true
        try {
            helper.initialize(
                onConnected = {
                    connecting = false
                    helper.addListener(listener)
                    _state.update { it.copy(isConnected = true) }
                    refresh()
                    takePending().forEach { it.first() }
                },
                onFailed = {
                    connecting = false
                    takePending().forEach { it.second() }
                },
            )
        } catch (_: Exception) {
            connecting = false
            takePending().forEach { it.second() }
        }
    }

    private fun takePending() = synchronized(pending) { pending.toList().also { pending.clear() } }

    /** 남은 시간은 이벤트로 오지 않을 수도 있어 주기적으로 다시 묻는다 */
    fun refresh() {
        if (!_state.value.isConnected) return
        try {
            helper.getDestinationInfo()
        } catch (_: Exception) {
        }
    }

    fun addWaypoint(request: WaypointRequest, onResult: (NaviCommandResult) -> Unit) {
        val submit = {
            onResult(
                try {
                    helper.addWaypoint(
                        RequestWaypointInfo(
                            latitude = request.latitude,
                            longitude = request.longitude,
                            waypointIndex = WaypointIndex.FIRST,
                            poiId = request.naviPoiId,
                            poiName = request.name,
                            poiSubId = request.naviPoiSubId,
                            address = request.address,
                        ),
                    )
                    NaviCommandResult.Submitted
                } catch (_: Exception) {
                    NaviCommandResult.Failed
                },
            )
        }
        when {
            _state.value.isConnected -> submit()
            connecting -> synchronized(pending) { pending += submit to { onResult(NaviCommandResult.NotConnected) } }
            else -> onResult(NaviCommandResult.NotConnected)
        }
    }

    /** 목적지로 길안내를 시작한다. 에뮬레이터에서는 내비 검색 입력이 어려워 시연용으로 쓴다 */
    fun requestRoute(name: String, latitude: Double, longitude: Double, address: String = ""): NaviCommandResult {
        if (!_state.value.isConnected) return NaviCommandResult.NotConnected
        return try {
            helper.requestRoute(
                RouteInfo(
                    latitude = latitude,
                    longitude = longitude,
                    poiName = name,
                    // 좌표만으로 길안내가 된다
                    poiId = "",
                    poiSubId = "",
                    address = address,
                    routeOption = RouteOption.RECOMMENDED,
                ),
            )
            NaviCommandResult.Submitted
        } catch (_: Exception) {
            NaviCommandResult.Failed
        }
    }

    private fun clearRoute() {
        _state.update { it.copy(isRouteActive = false, destinationName = "", routeDistanceKm = null, etaMinutes = null) }
    }

    fun release() {
        if (_state.value.isConnected) {
            try {
                helper.removeListener(listener)
                helper.release()
            } catch (_: Exception) {
            }
        }
        _state.value = NaviState()
    }
}

// SDK의 시간 값은 초 단위다
private fun Int.secondsToMinutes(): Int? = takeIf { it >= 0 }?.let { (it + 59) / 60 }
