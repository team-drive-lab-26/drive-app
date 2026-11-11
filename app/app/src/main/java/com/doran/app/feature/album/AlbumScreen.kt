package com.doran.app.feature.album

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.doran.app.core.designsystem.BackBar
import com.doran.app.core.designsystem.DoranPalette
import com.doran.app.core.designsystem.RoadScene
import com.doran.app.core.designsystem.RoadSide
import com.doran.app.core.designsystem.RoadSign

// 길가에 세울 여행 수. 더 오래된 여행은 지평선 너머로 넘긴다
private const val MAX_SIGNS = 6

/**
 * 다녀온 여행 목록. 정차 홈의 앞길을 뒤로 돌린 '지나온 길'이다.
 * 최근 여행일수록 가까운 길가에 표지판으로 서 있고, 오래된 여행일수록 지평선 쪽에 있다. 표지판을 누르면 그 여행의 3D 지도 앨범을 연다.
 */
@Composable
fun AlbumScreen(
    onOpenTrip: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: AlbumViewModel = viewModel(factory = AlbumViewModel.Factory),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val trips = ui.trips.take(MAX_SIGNS)
    val signs = trips.mapIndexed { i, trip ->
        RoadSign(
            id = trip.sessionId,
            title = trip.title,
            sub = if (trip.isCurrent) "지금 여행 · ${trip.detail}" else trip.detail,
            depth = 0.06f + 0.16f * i,
            side = if (i % 2 == 0) RoadSide.LEFT else RoadSide.RIGHT,
            hot = trip.isCurrent,
        )
    }
    Box(Modifier.fillMaxSize().background(DoranPalette.Background)) {
        RoadScene(signs = signs, horizon = 0.40f, showRoute = false, onSignClick = onOpenTrip)
        Column {
            BackBar(back = "도란", title = "지나온 길", onBack = onBack)
            Text(
                when {
                    ui.loading -> "기록을 불러오는 중이야"
                    ui.trips.isEmpty() -> "아직 다녀온 여행이 없어. 출발하면 여기 길가에 하나씩 서."
                    ui.offline -> "서버에 닿지 않아 이 기기에 있는 목소리만 보여 줄게."
                    else -> "여행마다 길가에 세워 뒀어. 눌러서 그날 길을 다시 봐."
                },
                style = MaterialTheme.typography.headlineMedium,
                color = DoranPalette.Ink,
                modifier = Modifier.padding(start = 48.dp).width(620.dp),
            )
        }
    }
}
