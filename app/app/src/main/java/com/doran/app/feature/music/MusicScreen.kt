package com.doran.app.feature.music

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.doran.app.core.data.AgentCard
import com.doran.app.core.designsystem.BackBar
import com.doran.app.core.designsystem.DoranFace
import com.doran.app.core.designsystem.DoranPalette
import com.doran.app.core.designsystem.DoranPrimaryButton
import com.doran.app.core.designsystem.RoadScene
import com.doran.app.core.designsystem.Rule
import com.doran.app.core.designsystem.TappableDoran
import kotlin.math.abs
import kotlin.math.sin

private val MOOD_NAMES = mapOf("travel" to "여행", "bright" to "밝은", "refresh" to "상쾌한", "calm" to "잔잔한")

private fun AgentCard.moodLine(): String {
    val moods = tags.mapNotNull(MOOD_NAMES::get).joinToString(", ")
    return if (moods.isBlank()) "" else "$moods 분위기"
}

/**
 * 음악. 같은 앞길 위에서 도란이 지금 어울리는 곡을 권한다. 들어오면 바로 추천을 받아 온다.
 * 왼쪽 위에 도란의 말과 가장 권하는 곡 하나(주 버튼), 오른쪽 위 패널에 나머지 곡. 재생 중인 곡은 막대가 움직인다.
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
    val first = tracks.firstOrNull()
    Box(Modifier.fillMaxSize().background(DoranPalette.Background)) {
        RoadScene(horizon = 0.40f, orbSize = 160.dp) {
            TappableDoran(face = face, onCall = onCall, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.width(620.dp)) {
            BackBar(back = "도란", title = "음악", onBack = onBack)
            Column(Modifier.padding(start = 48.dp)) {
                Text(
                    caption.ifBlank { if (first == null) "어떤 곡이 좋을지 골라 볼게" else "지금은 ${first.name} 어때?" },
                    style = MaterialTheme.typography.headlineLarge,
                    color = DoranPalette.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (first != null) {
                    Spacer(Modifier.size(20.dp))
                    if (first.trackId == nowPlaying) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Bars()
                            Spacer(Modifier.width(14.dp))
                            Text("${first.name} 재생 중", style = MaterialTheme.typography.titleLarge, color = DoranPalette.Accent)
                        }
                    } else {
                        DoranPrimaryButton(text = "${first.name} 틀기", onClick = { onPlay(first) })
                    }
                }
            }
        }
        if (tracks.size > 1) {
            val shape = RoundedCornerShape(22.dp)
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 112.dp, end = 40.dp)
                    .width(380.dp)
                    .clip(shape)
                    .background(DoranPalette.Surface)
                    .border(1.dp, DoranPalette.Line, shape),
            ) {
                Text("다른 곡", style = MaterialTheme.typography.titleMedium, color = DoranPalette.InkSoft, modifier = Modifier.padding(start = 24.dp, top = 18.dp, bottom = 6.dp))
                tracks.drop(1).forEachIndexed { index, track ->
                    if (index > 0) Rule(Modifier.padding(horizontal = 24.dp))
                    TrackRow(track, playing = track.trackId == nowPlaying, onPlay = { onPlay(track) })
                }
            }
        }
    }
}

@Composable
private fun TrackRow(track: AgentCard, playing: Boolean, onPlay: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .clickable(onClick = onPlay)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(track.name, style = MaterialTheme.typography.titleLarge, color = DoranPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val mood = track.moodLine()
            if (mood.isNotBlank()) Text(mood, style = MaterialTheme.typography.bodyMedium, color = DoranPalette.InkSoft, maxLines = 1)
        }
        if (playing) Bars() else Text("틀기", style = MaterialTheme.typography.labelLarge, color = DoranPalette.Accent)
    }
}

/** 재생 중 표시. 막대 네 개가 오르내린다 */
@Composable
private fun Bars() {
    val t by rememberInfiniteTransition(label = "bars").animateFloat(
        initialValue = 0f,
        targetValue = 6.283f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    Canvas(Modifier.size(36.dp)) {
        val w = size.width / 7f
        for (i in 0 until 4) {
            val h = size.height * (0.3f + 0.7f * abs(sin(t + i * 1.1f)))
            drawRoundRect(DoranPalette.Accent, topLeft = Offset(i * w * 1.8f, size.height - h), size = Size(w, h), cornerRadius = CornerRadius(w / 2f))
        }
    }
}
