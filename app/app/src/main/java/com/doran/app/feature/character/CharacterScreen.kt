package com.doran.app.feature.character

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.doran.app.core.data.AgentCard
import com.doran.app.core.designsystem.DoranIcon
import com.doran.app.core.designsystem.NavLink
import com.doran.app.core.designsystem.SayLine
import com.doran.app.core.model.VehicleState
import com.doran.app.core.designsystem.DoranFace
import com.doran.app.core.designsystem.DoranPalette
import com.doran.app.core.designsystem.RoadScene
import com.doran.app.core.designsystem.RoadSide
import com.doran.app.core.designsystem.RoadSign
import com.doran.app.core.designsystem.SuggestionCard
import com.doran.app.core.designsystem.TappableDoran
import com.doran.app.core.designsystem.TopBar
import com.doran.app.core.designsystem.depthForKm
import com.doran.app.core.model.AheadStop

/** 정차 화면에서 바로 갈 수 있는 곳 */
class HomeLinks(val onAlbum: () -> Unit, val onMusic: () -> Unit, val onSettings: () -> Unit)

/** 앞길 정보. 목적지, 도착 시각, 길가 휴게소, 내비에 넣은 경유지 */
class RoadAhead(val destination: String, val arrival: String, val stops: List<AheadStop>, val detour: String = "")

/**
 * 정차 중 첫 화면. 차 앞 도로가 화면 전체에 펼쳐지고 도란은 내 차 자리에 있다.
 * 길가에는 앞으로 지날 휴게소가 실제 거리만큼 떨어져 서 있고, 지평선 끝이 목적지다.
 * 도란이 제안하면 그곳이 왼쪽 길가에 주황 표지판으로 솟고, 오른쪽에 사진 패널이 붙는다(최대 2곳).
 */
@Composable
fun CharacterScreen(
    face: DoranFace,
    caption: String,
    eta: String,
    vehicle: VehicleState,
    ahead: RoadAhead,
    cards: List<AgentCard>,
    links: HomeLinks,
    onCall: () -> Unit,
    onLongPress: () -> Unit,
    onChoose: (AgentCard) -> Unit,
    onDismissCards: () -> Unit,
) {
    val shown = cards.take(2)
    val cardNames = shown.map { it.name }.toSet()
    val signs = buildList {
        // 제안이 떠 있으면 앞길 표지판은 제안한 곳만 남긴다. 이미 길가에 있는 곳을 제안하면 그 표지판을 주황으로 바꾼다
        ahead.stops.forEach { stop ->
            val isDetour = stop.name == ahead.detour
            val hot = stop.name in cardNames || isDetour
            val sub = if (isDetour) "경유지 · ${"%.0f".format(stop.km)}km" else "${"%.0f".format(stop.km)}km"
            if (shown.isEmpty() || hot) add(RoadSign("ahead-${stop.name}", stop.name, sub, depthForKm(stop.km), RoadSide.RIGHT, hot))
        }
        // 길가 표지판에 없는 곳을 경유지로 넣었으면 왼쪽 길가에 세운다
        if (ahead.detour.isNotBlank() && ahead.stops.none { it.name == ahead.detour }) {
            add(RoadSign("ahead-${ahead.detour}", ahead.detour, "경유지", 0.16f, RoadSide.LEFT, hot = true))
        }
        if (ahead.destination.isNotBlank()) add(RoadSign("dest", ahead.destination, ahead.arrival, 0.97f, RoadSide.CENTER))
        shown.filter { card -> ahead.stops.none { it.name == card.name } }.forEachIndexed { i, card ->
            val off = card.distanceKm
            val sub = if (off == null || off < 0.5) "경로 위" else "경로에서 ${"%.1f".format(off)}km"
            add(RoadSign("card-${card.name}", card.name, sub, 0.10f + 0.15f * i, RoadSide.LEFT, hot = true))
        }
    }
    val orb by animateDpAsState(if (shown.isEmpty()) 160.dp else 130.dp, tween(420), label = "orb")
    val via = ahead.detour.takeIf { it.isNotBlank() }?.let { "ahead-$it" }

    Box(
        Modifier
            .fillMaxSize()
            .background(DoranPalette.Background),
    ) {
        RoadScene(signs = signs, horizon = 0.40f, orbSize = orb, via = via, flow = shown.isEmpty()) {
            TappableDoran(face = face, onCall = onCall, onLongPress = onLongPress, modifier = Modifier.fillMaxSize())
        }
        TopBar(
            destination = ahead.destination,
            eta = eta,
            arrival = ahead.arrival,
            vehicle = vehicle,
            links = listOf(
                NavLink("앨범", DoranIcon.SIGNPOST, links.onAlbum),
                NavLink("음악", DoranIcon.NOTE, links.onMusic),
                NavLink("설정", DoranIcon.SLIDERS, links.onSettings),
            ),
        )
        SayLine(
            caption,
            maxLines = if (shown.isEmpty()) 2 else 3,
            modifier = Modifier
                .padding(start = 48.dp, top = if (vehicle.hasStatus) 214.dp else 180.dp)
                .width(if (shown.isEmpty()) 820.dp else 660.dp),
        )
        AnimatedVisibility(
            visible = shown.isNotEmpty(),
            enter = fadeIn(tween(240, delayMillis = 160)) + slideInHorizontally(tween(420, delayMillis = 160)) { it / 4 },
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 128.dp, end = 32.dp),
        ) {
            Column(Modifier.width(400.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                shown.forEachIndexed { i, card ->
                    SuggestionCard(card = card, onAccept = { onChoose(card) }, onDismiss = onDismissCards, compact = i > 0, batteryPercent = vehicle.batteryPercent)
                }
            }
        }
    }
}

private val VehicleState.hasStatus: Boolean get() = batteryPercent != null || (buckledSeats ?: 0) > 0
