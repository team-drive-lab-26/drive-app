package com.doran.app.feature.album

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.doran.app.core.designsystem.BackBar
import com.doran.app.core.designsystem.DoranPalette
import com.doran.app.core.designsystem.DoranText
import com.doran.app.core.designsystem.RoadScene

/**
 * 다녀온 여행 목록. 정차 홈의 앞길을 뒤로 돌린 '지나온 길'이 배경에 깔리고, 여행이 한 줄씩 놓인다.
 * 하나를 누르면 그 여행의 3D 지도 앨범을 연다.
 */
@Composable
fun AlbumScreen(
    onOpenTrip: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: AlbumViewModel = viewModel(factory = AlbumViewModel.Factory),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(DoranPalette.Background)) {
        // 배경의 지나온 길. 경로선 없이 길만
        RoadScene(horizon = 0.5f, showRoute = false, modifier = Modifier.padding(start = 0.dp))
        Column(Modifier.fillMaxSize()) {
            BackBar(back = "도란", title = "지나온 길", onBack = onBack)
            Row(Modifier.fillMaxSize().padding(start = 48.dp, end = 40.dp, bottom = 32.dp)) {
                Text(
                    when {
                        ui.loading -> "기록을 불러오는 중이야"
                        ui.trips.isEmpty() -> "아직 다녀온 여행이 없어. 출발하면 여기에 쌓여."
                        ui.offline -> "서버에 닿지 않아 이 기기에 있는 목소리만 보여 줄게."
                        else -> "여행마다 다녀온 길과 들른 곳, 남긴 목소리를 지도에 모아 뒀어."
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = DoranPalette.Ink,
                    modifier = Modifier.weight(0.7f).padding(end = 40.dp, top = 8.dp),
                )
                LazyColumn(Modifier.weight(1.3f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(ui.trips, key = { it.sessionId }) { trip ->
                        TripRow(trip, onClick = { onOpenTrip(trip.sessionId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TripRow(trip: TripItem, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp)
            .clip(shape)
            .background(DoranPalette.Surface)
            .border(1.dp, if (trip.isCurrent) DoranPalette.Accent else DoranPalette.Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(trip.title, style = MaterialTheme.typography.headlineMedium, color = DoranPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(trip.detail, style = MaterialTheme.typography.bodyMedium, color = DoranPalette.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(20.dp))
        Column(horizontalAlignment = Alignment.End) {
            if (trip.isCurrent) Text("지금 여행", style = DoranText.number, color = DoranPalette.Accent)
            Text("지도로 보기", style = MaterialTheme.typography.titleLarge, color = DoranPalette.Ink)
        }
    }
}
