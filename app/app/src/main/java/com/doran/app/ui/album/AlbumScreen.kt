package com.doran.app.ui.album

import androidx.compose.runtime.Composable
import com.doran.app.ui.components.PlaceholderLink
import com.doran.app.ui.components.PlaceholderScreen

@Composable
fun AlbumScreen(
    onOpenMap: () -> Unit,
    onOpenCharacter: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRecord: () -> Unit,
    onOpenMusic: () -> Unit,
) {
    PlaceholderScreen(
        title = "앨범",
        links = listOf(
            PlaceholderLink("3D 지도 앨범", onOpenMap),
            PlaceholderLink("캐릭터 홈", onOpenCharacter),
            PlaceholderLink("설정", onOpenSettings),
            PlaceholderLink("녹음 (자동 전환 연결 전 임시)", onOpenRecord),
            PlaceholderLink("음악 추천 (자동 전환 연결 전 임시)", onOpenMusic),
        ),
    )
}
