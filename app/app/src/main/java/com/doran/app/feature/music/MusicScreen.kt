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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.doran.app.core.data.AgentCard
import com.doran.app.core.designsystem.BackBar
import com.doran.app.core.designsystem.DoranFace
import com.doran.app.core.designsystem.DoranPalette
import com.doran.app.core.designsystem.DoranText
import com.doran.app.core.designsystem.Rule
import com.doran.app.core.designsystem.TappableDoran

private val MOOD_NAMES = mapOf("travel" to "여행", "bright" to "밝은", "refresh" to "상쾌한", "calm" to "잔잔한")

/**
 * 도란이 지금 어울리는 곡을 세 곡까지 골라 준다. 들어오면 바로 추천을 받아 온다.
 * 곡 목록은 테이프 속지(A면 곡 목록)처럼 번호를 크게 적고, 재생 중인 곡은 테이프 색으로 뒤집혀 릴이 돈다.
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
        BoxWithConstraints(Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, bottom = 40.dp)) {
            val doran = minOf(maxHeight * 0.5f, 280.dp)
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(0.8f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    Text(
                        caption.ifBlank { "어떤 곡이 좋을지 골라 볼게" },
                        style = MaterialTheme.typography.headlineMedium,
                        color = DoranPalette.Ink,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 40.dp),
                    )
                    Spacer(Modifier.size(24.dp))
                    TappableDoran(face = face, onCall = onCall, modifier = Modifier.size(doran))
                }
                Column(
                    Modifier
                        .weight(1.2f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    JCard(tracks, nowPlaying, onPlay)
                }
            }
        }
    }
}

/** 테이프 속지. 흰 종이에 A면 곡 목록 */
@Composable
private fun JCard(tracks: List<AgentCard>, nowPlaying: String?, onPlay: (AgentCard) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DoranPalette.Surface)
            .padding(vertical = 12.dp),
    ) {
        if (tracks.isEmpty()) {
            Text(
                "도란이 고르는 중이야",
                style = MaterialTheme.typography.bodyLarge,
                color = DoranPalette.InkSoft,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 28.dp),
            )
        }
        tracks.forEachIndexed { index, track ->
            if (index > 0) Rule(Modifier.padding(horizontal = 28.dp), DoranPalette.LineOnPaper)
            TrackRow("A${index + 1}", track, playing = track.trackId == nowPlaying, first = index == 0, onPlay = { onPlay(track) })
        }
    }
}

@Composable
private fun TrackRow(number: String, track: AgentCard, playing: Boolean, first: Boolean, onPlay: () -> Unit) {
    val ink = if (playing) DoranPalette.TapeText else DoranPalette.Ink
    val soft = if (playing) DoranPalette.TapeTextSoft else DoranPalette.InkSoft
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp)
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (playing) DoranPalette.Tape else Color.Transparent)
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(number, style = DoranText.trackNo, color = if (playing) DoranPalette.Head else ink, modifier = Modifier.width(72.dp))
        Column(Modifier.weight(1f)) {
            Text(track.name, style = MaterialTheme.typography.headlineMedium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val moods = track.tags.mapNotNull(MOOD_NAMES::get).joinToString(", ")
            Text(
                listOf(if ("더미" in track.artist) "시연용 곡" else track.artist, if (moods.isBlank()) "" else "$moods 분위기").filter(String::isNotBlank).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = soft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(16.dp))
        if (playing) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Reel()
                Reel()
            }
        } else {
            Box(
                Modifier
                    .heightIn(min = 64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (first) DoranPalette.Ink else Color.Transparent)
                    .border(2.dp, DoranPalette.Ink, RoundedCornerShape(14.dp))
                    .padding(horizontal = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("틀기", style = MaterialTheme.typography.labelLarge, color = if (first) DoranPalette.Surface else DoranPalette.Ink, modifier = Modifier.padding(vertical = 20.dp))
            }
        }
    }
}

/** 재생 중 표시. 테이프 릴이 천천히 돈다 */
@Composable
private fun Reel() {
    val turn by rememberInfiniteTransition(label = "reel").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "turn",
    )
    Canvas(Modifier.size(44.dp).rotate(turn)) {
        val r = size.minDimension / 2f
        drawCircle(DoranPalette.TapeText, r - 2.dp.toPx(), style = Stroke(width = 3.dp.toPx()))
        drawCircle(DoranPalette.TapeText, r * 0.28f)
        for (i in 0 until 3) {
            val a = Math.toRadians(i * 120.0)
            val inner = Offset(center.x + (r * 0.28f * Math.cos(a)).toFloat(), center.y + (r * 0.28f * Math.sin(a)).toFloat())
            val outer = Offset(center.x + (r * 0.72f * Math.cos(a)).toFloat(), center.y + (r * 0.72f * Math.sin(a)).toFloat())
            drawLine(DoranPalette.Head, inner, outer, strokeWidth = 4.dp.toPx())
        }
    }
}
