package com.doran.app.feature.album

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.doran.app.core.designsystem.BackBar
import com.doran.app.core.designsystem.DoranIcon
import com.doran.app.core.designsystem.DoranPalette
import com.doran.app.core.designsystem.DoranPrimaryButton
import com.doran.app.core.designsystem.DoranText
import com.doran.app.core.designsystem.Glyph
import com.doran.app.core.designsystem.PersonDot
import com.doran.app.core.designsystem.RoadScene
import com.doran.app.core.designsystem.RoadSide
import com.doran.app.core.designsystem.RoadSign
import com.doran.app.core.designsystem.SayLine
import com.doran.app.core.designsystem.panel

// 길가에 세울 여행 수. 더 오래된 여행은 지평선 너머로 넘긴다
private const val MAX_SIGNS = 6

// 표지판 깊이. 모두 왼쪽 길가(오른쪽은 패널)에 서므로 판이 겹치지 않게 멀수록 간격을 벌린다
private val DEPTHS = listOf(0.07f, 0.30f, 0.60f, 0.78f, 0.88f, 0.94f)

/**
 * 다녀온 여행 목록. 정차 홈의 앞길을 뒤로 돌린 '지나온 길'이다.
 * 최근 여행일수록 가까운 길가에 표지판으로 서 있고, 오래된 여행일수록 지평선 쪽에 있다.
 * 표지판을 고르면 오른쪽 패널에 그 여행에서 들른 곳과 남긴 목소리가 순서대로 보이고, 거기서 3D 지도 앨범을 연다.
 * 여행이 적으면 빈 자리에 점선 표지판("다음 여행")을 세워 길이 비어 보이지 않게 한다.
 */
@Composable
fun AlbumScreen(
    onOpenTrip: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: AlbumViewModel = viewModel(factory = AlbumViewModel.Factory),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val trips = ui.trips.take(MAX_SIGNS)
    val signs = buildList {
        trips.forEachIndexed { i, trip ->
            add(
                RoadSign(
                    id = trip.sessionId,
                    title = trip.title,
                    sub = if (trip.isCurrent) "지금 여행 · ${trip.detail}" else trip.detail,
                    depth = DEPTHS[i],
                    side = RoadSide.LEFT,
                    hot = trip.isCurrent,
                    selected = trip.sessionId == ui.selectedId,
                ),
            )
        }
        if (!ui.loading && trips.size < 3) {
            val i = trips.size
            add(RoadSign("next", "다음 여행", "출발하면 여기 서", DEPTHS[i], RoadSide.LEFT, ghost = true))
        }
    }
    Box(Modifier.fillMaxSize().background(DoranPalette.Background)) {
        RoadScene(
            signs = signs,
            horizon = 0.40f,
            showRoute = false,
            orbLight = false,
            onSignClick = { id -> if (id == ui.selectedId) onOpenTrip(id) else viewModel.select(id) },
        )
        Column(Modifier.width(760.dp)) {
            BackBar(back = "도란", title = "지나온 길", onBack = onBack)
            SayLine(
                when {
                    ui.loading -> "기록을 불러오는 중이야"
                    ui.trips.isEmpty() -> "아직 지나온 길이 없어"
                    ui.offline -> "서버에 닿지 않아 이 기기에 있는 목소리만 보여 줄게."
                    ui.trips.size == 1 -> "첫 여행이 길가에 서 있어. 눌러서 그날 길을 다시 봐."
                    else -> "여행 ${ui.trips.size}번을 길가에 세워 뒀어. 가까울수록 최근이야."
                },
                maxLines = 3,
                modifier = Modifier.padding(start = 48.dp, top = 8.dp),
            )
            if (!ui.loading && ui.trips.isEmpty()) {
                Text(
                    "여행 중에 들른 곳에서 목소리를 남기면, 그 여행이 여기 길가에 표지판으로 하나씩 서.",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 24.sp, lineHeight = 36.sp),
                    color = DoranPalette.InkSoft,
                    modifier = Modifier.padding(start = 82.dp, top = 12.dp, end = 120.dp),
                )
            }
        }
        val selected = ui.selected
        if (selected != null) {
            AnimatedContent(
                targetState = selected.sessionId,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                label = "trip",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 124.dp, end = 32.dp, bottom = 32.dp)
                    .width(440.dp),
            ) { id ->
                val trip = ui.trips.firstOrNull { it.sessionId == id } ?: return@AnimatedContent
                TripPanel(trip, ui.preview?.takeIf { it.sessionId == id }, onOpen = { onOpenTrip(id) })
            }
        }
    }
}

/** 고른 여행. 날짜, 들른 곳을 순서대로 잇는 세로 길, 지도 앨범 열기 */
@Composable
private fun TripPanel(trip: TripItem, preview: TripPreview?, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().panel().padding(horizontal = 28.dp, vertical = 26.dp)) {
        Text(trip.title, style = MaterialTheme.typography.headlineLarge, color = DoranPalette.Ink)
        Spacer(Modifier.height(4.dp))
        Text(trip.detail, style = DoranText.etaSide.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize), color = DoranPalette.InkSoft)
        Spacer(Modifier.height(20.dp))
        when {
            preview == null || preview.loading -> Text("들른 곳을 불러오는 중이야", style = MaterialTheme.typography.bodyLarge, color = DoranPalette.InkSoft)
            preview.stops.isEmpty() -> Text(
                if (preview.voiceCount > 0) "들른 곳 기록은 없고 목소리 ${preview.voiceCount}개가 남아 있어." else "이 여행에는 아직 들른 곳이 없어.",
                style = MaterialTheme.typography.bodyLarge,
                color = DoranPalette.InkSoft,
            )
            else -> Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                preview.stops.forEachIndexed { i, stop -> StopLine(stop, first = i == 0, last = i == preview.stops.lastIndex) }
            }
        }
        Spacer(Modifier.height(24.dp))
        DoranPrimaryButton(text = "지도 앨범 열기", onClick = onOpen, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp))
    }
}

/** 들른 곳 한 줄. 왼쪽 세로 선이 위아래 곳을 길처럼 잇는다 */
@Composable
private fun StopLine(stop: TripStop, first: Boolean, last: Boolean) {
    Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).height(IntrinsicSize.Min)) {
        Box(Modifier.width(28.dp).fillMaxHeight()) {
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width / 2f
                val dotY = 22.dp.toPx()
                if (!first) drawLine(DoranPalette.LineStrong, Offset(x, 0f), Offset(x, dotY), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                if (!last) drawLine(DoranPalette.LineStrong, Offset(x, dotY), Offset(x, size.height), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(if (stop.voices.isNotEmpty()) DoranPalette.Accent else DoranPalette.InkSoft, 8.dp.toPx(), Offset(x, dotY))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(top = 6.dp, bottom = 10.dp)) {
            Text(stop.name, style = MaterialTheme.typography.titleLarge, color = DoranPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (stop.time.isNotBlank()) Text(stop.time, style = DoranText.number, color = DoranPalette.InkSoft)
                if (stop.voices.isNotEmpty()) {
                    Spacer(Modifier.width(14.dp))
                    Glyph(DoranIcon.VOICE, DoranPalette.InkSoft, size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                    stop.voices.forEach { name ->
                        PersonDot(name, Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(name, style = MaterialTheme.typography.bodyMedium, color = DoranPalette.InkSoft)
                        Spacer(Modifier.width(10.dp))
                    }
                }
            }
        }
    }
}
