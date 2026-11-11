package com.doran.app.feature.album

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.doran.app.core.designsystem.DoranCharacter
import com.doran.app.core.designsystem.DoranMood
import com.doran.app.core.designsystem.DoranPalette

/** 다녀온 여행 목록. 하나를 누르면 그 여행의 3D 지도 앨범을 연다 */
@Composable
fun AlbumScreen(
    onOpenTrip: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: AlbumViewModel = viewModel(factory = AlbumViewModel.Factory),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    Row(
        Modifier
            .fillMaxSize()
            .background(DoranPalette.Background)
            .padding(horizontal = 40.dp, vertical = 24.dp),
    ) {
        Column(Modifier.weight(0.8f).fillMaxHeight()) {
            Text(
                "← 돌아가기",
                style = MaterialTheme.typography.titleLarge,
                color = DoranPalette.Ink,
                modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp),
            )
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                DoranCharacter(mood = DoranMood.IDLE, modifier = Modifier.size(260.dp))
            }
        }
        Spacer(Modifier.width(40.dp))
        Column(Modifier.weight(1.2f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            Text("여행 앨범", style = MaterialTheme.typography.headlineMedium, color = DoranPalette.Ink)
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    ui.loading -> "기록을 불러오는 중이야"
                    ui.trips.isEmpty() -> "아직 다녀온 여행이 없어. 출발하면 여기에 쌓여."
                    ui.offline -> "서버에 닿지 않아 이 기기에 있는 목소리만 보여 줄게."
                    else -> "다녀온 길과 들른 곳, 남긴 목소리를 지도에 모아 뒀어."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = DoranPalette.InkSoft,
            )
            Spacer(Modifier.height(24.dp))
            LazyColumn {
                itemsIndexed(ui.trips, key = { _, trip -> trip.sessionId }) { index, trip ->
                    if (index > 0) HorizontalDivider(color = DoranPalette.Line)
                    TripRow(trip, onClick = { onOpenTrip(trip.sessionId) })
                }
            }
        }
    }
}

@Composable
private fun TripRow(trip: TripItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (trip.isCurrent) "${trip.title} (지금 여행)" else trip.title,
                style = MaterialTheme.typography.titleLarge,
                color = DoranPalette.Ink,
            )
            Text(trip.detail, style = MaterialTheme.typography.bodyMedium, color = DoranPalette.InkSoft)
        }
        Text("지도 보기", style = MaterialTheme.typography.labelLarge, color = DoranPalette.Body)
    }
}
