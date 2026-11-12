package com.doran.app.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 화면을 낮으로 그릴지 정하는 유일한 곳. 화면은 [isDay]만 받아 쓴다.
 * 설정이 낮·밤이면 그대로, 자동이면 전조등을 따르고(실제 차 내비처럼), 전조등 값을 못 받는 차는 시각으로 정한다.
 */
class DaylightSource(
    scope: CoroutineScope,
    preferences: AppPreferences,
    vehicle: VehicleStateSource,
    private val now: () -> LocalDateTime = { LocalDateTime.now(SEOUL) },
) {
    val isDay: StateFlow<Boolean> = combine(
        preferences.state.map { it.screen }.distinctUntilChanged(),
        vehicle.state.map { it.isHeadlightOn }.distinctUntilChanged(),
        clockIsDay(),
    ) { screen, headlightOn, clockDay -> decide(screen, headlightOn, clockDay) }
        .stateIn(scope, SharingStarted.Eagerly, decide(preferences.state.value.screen, vehicle.state.value.isHeadlightOn, isDayAt(now())))

    // 매분 확인하지 않고 다음 7시나 19시까지 한 번만 기다린다
    private fun clockIsDay(): Flow<Boolean> = flow {
        while (true) {
            val time = now()
            emit(isDayAt(time))
            delay(untilNextSwitch(time).toMillis() + 1_000)
        }
    }

    companion object {
        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
        const val DAY_START_HOUR = 7
        const val NIGHT_START_HOUR = 19

        fun decide(screen: String, headlightOn: Boolean?, clockDay: Boolean): Boolean = when (screen) {
            Preferences.SCREEN_DAY -> true
            Preferences.SCREEN_NIGHT -> false
            else -> headlightOn?.not() ?: clockDay
        }

        fun isDayAt(time: LocalDateTime): Boolean = time.hour in DAY_START_HOUR until NIGHT_START_HOUR

        fun untilNextSwitch(time: LocalDateTime): Duration {
            val today = time.toLocalDate()
            val next = listOf(
                today.atTime(DAY_START_HOUR, 0),
                today.atTime(NIGHT_START_HOUR, 0),
                today.plusDays(1).atTime(DAY_START_HOUR, 0),
            ).first { it.isAfter(time) }
            return Duration.between(time, next)
        }
    }
}
