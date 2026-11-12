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
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import com.doran.app.core.designsystem.DoranPrimaryButton
import com.doran.app.core.designsystem.DoranSecondaryButton
import com.doran.app.core.designsystem.DoranText
import com.doran.app.core.designsystem.Glyph
import com.doran.app.core.designsystem.panel
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
 * 들른 곳에 세우면([askingRecord]) 그곳이 바로 앞 길가에 "지금 세운 곳"으로 서고, 오른쪽에 한마디 남길지 묻는 패널이 붙는다.
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
    askingRecord: Boolean = false,
    stoppedAt: String = "",
    onAnswerRecord: (Boolean) -> Unit = {},
) {
    val shown = cards.take(2)
    // 제안 카드가 먼저 떠 있으면 그쪽을 보인다. 둘이 겹치지 않게 한다
    val asking = askingRecord && shown.isEmpty() && stoppedAt.isNotBlank()
    val cardNames = shown.map { it.name }.toSet()
    val signs = buildList {
        // 제안이 떠 있으면 앞길 표지판은 제안한 곳만 남긴다. 이미 길가에 있는 곳을 제안하면 그 표지판을 주황으로 바꾼다
        ahead.stops.forEach { stop ->
            // 막 세운 곳은 앞길이 아니라 바로 옆 길가로 옮겨 세운다
            if (asking && stop.name == stoppedAt) return@forEach
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
        if (asking) add(RoadSign("here-$stoppedAt", stoppedAt, "지금 세운 곳", 0.06f, RoadSide.RIGHT, hot = true))
        shown.filter { card -> ahead.stops.none { it.name == card.name } }.forEachIndexed { i, card ->
            val off = card.distanceKm
            val sub = if (off == null || off < 0.5) "경로 위" else "경로에서 ${"%.1f".format(off)}km"
            add(RoadSign("card-${card.name}", card.name, sub, 0.10f + 0.15f * i, RoadSide.LEFT, hot = true))
        }
    }
    val orb by animateDpAsState(if (shown.isEmpty() && !asking) 160.dp else 130.dp, tween(420), label = "orb")
    val via = ahead.detour.takeIf { it.isNotBlank() }?.let { "ahead-$it" }

    Box(
        Modifier
            .fillMaxSize()
            .background(DoranPalette.Background),
    ) {
        RoadScene(signs = signs, horizon = 0.40f, orbSize = orb, via = via, flow = shown.isEmpty() && !asking) {
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
            maxLines = if (shown.isEmpty() && !asking) 2 else 3,
            modifier = Modifier
                .padding(start = 48.dp, top = if (vehicle.hasStatus) 214.dp else 180.dp)
                .width(if (shown.isEmpty() && !asking) 820.dp else 660.dp),
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
        AnimatedVisibility(
            visible = asking,
            enter = fadeIn(tween(240, delayMillis = 160)) + slideInHorizontally(tween(420, delayMillis = 160)) { it / 4 },
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 128.dp, end = 32.dp),
        ) {
            RecordAskPanel(place = stoppedAt, onAnswer = onAnswerRecord, modifier = Modifier.width(400.dp))
        }
    }
}

/**
 * 들른 곳에서 한마디 남길지 묻는 패널. 말("응", "괜찮아")로도 답할 수 있어 버튼은 두 개만 둔다.
 * 남기면 녹음 화면으로 넘어가 15초 동안 듣고, 앨범 지도 이 자리에 남긴 사람 색 핀으로 꽂힌다.
 */
@Composable
private fun RecordAskPanel(place: String, onAnswer: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.panel().padding(horizontal = 28.dp, vertical = 26.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).background(DoranPalette.Accent, CircleShape), contentAlignment = Alignment.Center) {
                Glyph(DoranIcon.MIC, DoranPalette.OnAccent, size = 32.dp)
            }
            Spacer(Modifier.width(18.dp))
            Column {
                // 도란이 물은 말은 듣는 동안 자막에서 사라지므로 패널 제목으로 남겨 둔다
                Text("한마디 남길래?", style = MaterialTheme.typography.headlineMedium, color = DoranPalette.Ink)
                Text("$place · 15초", style = DoranText.number.copy(fontSize = 20.sp), color = DoranPalette.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "남기면 앨범 지도의 이 자리에 남긴 사람 색 핀으로 꽂혀.",
            style = MaterialTheme.typography.bodyLarge,
            color = DoranPalette.InkSoft,
        )
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DoranPrimaryButton(text = "남길래", onClick = { onAnswer(true) }, modifier = Modifier.weight(1.3f).heightIn(min = 72.dp))
            DoranSecondaryButton(text = "괜찮아", onClick = { onAnswer(false) }, modifier = Modifier.weight(1f).heightIn(min = 72.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text("\"응\"이나 \"괜찮아\"라고 말해도 돼", style = MaterialTheme.typography.bodyMedium, color = DoranPalette.InkMute)
    }
}

private val VehicleState.hasStatus: Boolean get() = batteryPercent != null || (buckledSeats ?: 0) > 0
