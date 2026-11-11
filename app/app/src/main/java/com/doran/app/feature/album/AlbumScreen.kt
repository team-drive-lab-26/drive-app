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
import com.doran.app.core.designsystem.DoranCharacter
import com.doran.app.core.designsystem.DoranMood
import com.doran.app.core.designsystem.DoranPalette
import com.doran.app.core.designsystem.DoranText

/**
 * 다녀온 여행 목록. 여행 하나가 테이프 하나라서, 테이프 등을 꽂아 둔 것처럼 줄을 세운다.
 * 손글씨 라벨에 여행 이름과 날짜, 오른쪽에 남긴 목소리 수. 하나를 누르면 그 여행의 3D 지도 앨범을 연다.
 */
@Composable
fun AlbumScreen(
    onOpenTrip: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: AlbumViewModel = viewModel(factory = AlbumViewModel.Factory),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxSize()
            .background(DoranPalette.Background),
    ) {
        BackBar(back = "도란", title = "여행 테이프", onBack = onBack)
        Row(Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, bottom = 32.dp)) {
            Column(Modifier.weight(0.75f).fillMaxHeight().padding(end = 40.dp)) {
                Text(
                    when {
                        ui.loading -> "기록을 불러오는 중이야"
                        ui.trips.isEmpty() -> "아직 다녀온 여행이 없어. 출발하면 여기에 한 개씩 쌓여."
                        ui.offline -> "서버에 닿지 않아 이 기기에 있는 목소리만 보여 줄게."
                        else -> "다녀온 길과 들른 곳, 남긴 목소리를 테이프마다 지도에 모아 뒀어."
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = DoranPalette.Ink,
                )
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
                    DoranCharacter(mood = DoranMood.IDLE, modifier = Modifier.size(240.dp))
                }
            }
            LazyColumn(
                Modifier.weight(1.25f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(ui.trips, key = { it.sessionId }) { trip ->
                    TapeSpine(trip, onClick = { onOpenTrip(trip.sessionId) })
                }
            }
        }
    }
}

/** 테이프 등. 갈색 띠 안에 흰 라벨을 붙이고 펜으로 여행 이름을 적는다 */
@Composable
private fun TapeSpine(trip: TripItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DoranPalette.Tape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(DoranPalette.Surface)
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text(trip.title, style = DoranText.label, color = DoranPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(trip.detail, style = MaterialTheme.typography.bodyMedium, color = DoranPalette.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(20.dp))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 10.dp)) {
            if (trip.isCurrent) Text("지금 여행", style = DoranText.time, color = DoranPalette.Head)
            Text("지도로 듣기", style = MaterialTheme.typography.titleLarge, color = DoranPalette.TapeText)
        }
    }
}
