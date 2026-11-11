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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.doran.app.core.data.AgentCard
import com.doran.app.core.designsystem.BackBar
import com.doran.app.core.designsystem.DoranButtonShape
import com.doran.app.core.designsystem.DoranFace
import com.doran.app.core.designsystem.DoranPalette
import com.doran.app.core.designsystem.DoranText
import com.doran.app.core.designsystem.TappableDoran
import kotlin.math.abs
import kotlin.math.sin

private val MOOD_NAMES = mapOf("travel" to "여행", "bright" to "밝은", "refresh" to "상쾌한", "calm" to "잔잔한")

/**
 * 도란이 지금 어울리는 곡을 세 곡까지 골라 준다. 들어오면 바로 추천을 받아 온다.
 * 첫 곡이 도란이 가장 권하는 곡이라 그 버튼만 채운다. 재생 중인 곡은 막대가 움직인다.
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
    Column(
        Modifier
            .fillMaxSize()
            .background(DoranPalette.Background),
    ) {
        BackBar(back = "도란", title = "지금 어울리는 곡", onBack = onBack)
        Row(Modifier.fillMaxSize().padding(start = 48.dp, end = 40.dp, bottom = 40.dp)) {
            Column(Modifier.weight(0.8f).fillMaxHeight().padding(end = 40.dp), verticalArrangement = Arrangement.Center) {
                TappableDoran(face = face, onCall = onCall, modifier = Modifier.size(200.dp))
                Spacer(Modifier.size(20.dp))
                Text(
                    caption.ifBlank { "어떤 곡이 좋을지 골라 볼게" },
                    style = MaterialTheme.typography.headlineMedium,
                    color = DoranPalette.Ink,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(Modifier.weight(1.2f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
                if (tracks.isEmpty()) {
                    Text("도란이 고르는 중이야", style = MaterialTheme.typography.bodyLarge, color = DoranPalette.InkSoft)
                }
                tracks.forEachIndexed { index, track ->
                    TrackRow(index + 1, track, playing = track.trackId == nowPlaying, first = index == 0, onPlay = { onPlay(track) })
                }
            }
        }
    }
}

@Composable
private fun TrackRow(number: Int, track: AgentCard, playing: Boolean, first: Boolean, onPlay: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp)
            .clip(shape)
            .background(if (playing) DoranPalette.SurfaceHigh else DoranPalette.Surface)
            .border(1.dp, if (playing) DoranPalette.Accent else DoranPalette.Line, shape)
            .clickable(onClick = onPlay)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(56.dp)) {
            if (playing) Bars() else Text("$number", style = DoranText.number.copy(fontSize = MaterialTheme.typography.headlineMedium.fontSize), color = DoranPalette.InkMute)
        }
        Column(Modifier.weight(1f)) {
            Text(track.name, style = MaterialTheme.typography.headlineMedium, color = DoranPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val moods = track.tags.mapNotNull(MOOD_NAMES::get).joinToString(", ")
            Text(
                listOf(if ("더미" in track.artist) "시연용 곡" else track.artist, if (moods.isBlank()) "" else "$moods 분위기")
                    .filter(String::isNotBlank).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = DoranPalette.InkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(16.dp))
        if (!playing) {
            Box(
                Modifier
                    .heightIn(min = 64.dp)
                    .clip(DoranButtonShape)
                    .background(if (first) DoranPalette.Accent else Color.Transparent)
                    .border(2.dp, if (first) DoranPalette.Accent else DoranPalette.LineStrong, DoranButtonShape)
                    .padding(horizontal = 30.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("틀기", style = MaterialTheme.typography.labelLarge, color = if (first) DoranPalette.OnAccent else DoranPalette.Ink, modifier = Modifier.padding(vertical = 19.dp))
            }
        } else {
            Text("재생 중", style = MaterialTheme.typography.labelLarge, color = DoranPalette.Accent)
        }
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
