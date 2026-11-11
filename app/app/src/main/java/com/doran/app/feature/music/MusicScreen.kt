package com.doran.app.feature.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.doran.app.core.data.AgentCard
import com.doran.app.core.designsystem.DoranFace
import com.doran.app.core.designsystem.DoranPalette
import com.doran.app.core.designsystem.DoranStage

private val MOOD_NAMES = mapOf("travel" to "여행", "bright" to "밝은", "refresh" to "상쾌한", "calm" to "잔잔한")

// 분위기마다 곡 앞 동그라미 색을 다르게 해 목록에서 구분한다
private val MOOD_COLORS = mapOf(
    "travel" to Color(0xFFFF7A32),
    "bright" to Color(0xFFF2B33D),
    "refresh" to Color(0xFF4FA3A5),
    "calm" to Color(0xFF7D86B5),
)

/**
 * 도란이 지금 어울리는 곡을 세 곡까지 골라 준다. 들어오면 바로 추천을 받아 온다.
 * 누르거나 "첫 번째 거 틀어 줘"처럼 말로 고른다.
 */
@Composable
fun MusicScreen(
    face: DoranFace,
    caption: String,
    tracks: List<AgentCard>,
    nowPlaying: String?,
    onEnter: () -> Unit,
    onPlay: (AgentCard) -> Unit,
    onCall: () -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) { onEnter() }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(DoranPalette.Background)
            .padding(horizontal = 40.dp, vertical = 24.dp),
    ) {
        val height = maxHeight
        Column(Modifier.fillMaxSize()) {
            Text(
                "← 돌아가기",
                style = MaterialTheme.typography.titleLarge,
                color = DoranPalette.Ink,
                modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp),
            )
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(0.8f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    DoranStage(face = face, caption = caption, onCall = onCall, characterSize = minOf(height * 0.48f, 300.dp))
                }
                Column(Modifier.weight(1.2f)) {
                    Text("지금 어울리는 곡", style = MaterialTheme.typography.headlineMedium, color = DoranPalette.Ink)
                    Spacer(Modifier.height(20.dp))
                    if (tracks.isEmpty()) {
                        Text("도란이 고르는 중이야", style = MaterialTheme.typography.bodyLarge, color = DoranPalette.InkSoft)
                    }
                    tracks.forEachIndexed { index, track ->
                        if (index > 0) HorizontalDivider(color = DoranPalette.Line)
                        TrackRow(track, playing = track.trackId == nowPlaying, onPlay = { onPlay(track) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(track: AgentCard, playing: Boolean, onPlay: () -> Unit) {
    val mood = track.tags.firstOrNull()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onPlay)
            .padding(vertical = 16.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(MOOD_COLORS[mood] ?: DoranPalette.Line))
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(track.name, style = MaterialTheme.typography.titleLarge, color = DoranPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val moods = track.tags.mapNotNull(MOOD_NAMES::get).joinToString(", ")
            Text(
                listOf(track.artist, if (moods.isBlank()) "" else "$moods 분위기").filter(String::isNotBlank).joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
                color = DoranPalette.InkSoft,
            )
        }
        Text(
            if (playing) "재생 중" else "틀기",
            style = MaterialTheme.typography.labelLarge,
            color = if (playing) DoranPalette.Body else DoranPalette.Ink,
        )
    }
}
