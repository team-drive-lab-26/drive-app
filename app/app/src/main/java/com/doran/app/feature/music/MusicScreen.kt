package com.doran.app.feature.music

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.doran.app.core.data.AgentCard
import com.doran.app.core.designsystem.BackBar
import com.doran.app.core.designsystem.DoranFace
import com.doran.app.core.designsystem.DoranIcon
import com.doran.app.core.designsystem.DoranPalette
import com.doran.app.core.designsystem.DoranText
import com.doran.app.core.designsystem.Glyph
import com.doran.app.core.designsystem.RoadScene
import com.doran.app.core.designsystem.Rule
import com.doran.app.core.designsystem.SayLine
import com.doran.app.core.designsystem.TappableDoran
import com.doran.app.core.designsystem.panel
import com.doran.app.core.media.LocalTrack
import kotlin.math.abs
import kotlin.math.sin

private val MOOD_NAMES = mapOf("travel" to "여행", "bright" to "밝은", "refresh" to "상쾌한", "calm" to "잔잔한")

/** 목록 한 줄. 도란이 권한 곡은 서버 카드([card])가 있고 분위기를 안다 */
private class MusicItem(val id: String, val title: String, val artist: String, val mood: String, val card: AgentCard?)

private fun AgentCard.moodLine(): String {
    val moods = tags.mapNotNull(MOOD_NAMES::get).joinToString(", ")
    return if (moods.isBlank()) "" else "$moods 분위기"
}

/**
 * 음악. 같은 앞길 위에서 도란이 지금 어울리는 곡을 권한다. 들어오면 바로 추천을 받아 온다.
 * 왼쪽에 지금 곡(재생 중이면 그 곡, 아니면 도란이 가장 권하는 곡)과 재생·일시정지·다음 버튼,
 * 오른쪽 패널에 도란이 권한 곡부터 기기에 넣어 둔 곡 전체. 재생 중인 곡은 막대가 움직인다.
 * 누르거나 "첫 번째 거 틀어 줘"처럼 말로 고른다.
 */
@Composable
fun MusicScreen(
    face: DoranFace,
    caption: String,
    tracks: List<AgentCard>,
    library: List<LocalTrack>,
    nowPlaying: String?,
    paused: Boolean,
    onEnter: () -> Unit,
    onPlay: (AgentCard) -> Unit,
    onPlayLocal: (String) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCall: () -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) { onEnter() }
    val items = buildList {
        tracks.forEach { card ->
            val id = card.trackId ?: return@forEach
            add(MusicItem(id, card.name, card.artist, card.moodLine(), card))
        }
        library.filter { local -> none { it.id == local.id } }.forEach { add(MusicItem(it.id, it.title, it.artist, "", null)) }
    }
    val current = items.firstOrNull { it.id == nowPlaying }
        ?: nowPlaying?.let { id -> MusicItem(id, id, "", "", null) }
    val featured = current ?: items.firstOrNull()
    val playing = current != null && !paused

    fun play(item: MusicItem) = item.card?.let(onPlay) ?: onPlayLocal(item.id)

    Box(Modifier.fillMaxSize().background(DoranPalette.Background)) {
        RoadScene(horizon = 0.40f, orbSize = 160.dp, flow = playing) {
            TappableDoran(face = face, onCall = onCall, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.width(720.dp)) {
            BackBar(back = "도란", title = "음악", onBack = onBack)
            Column(Modifier.padding(start = 48.dp, top = 8.dp)) {
                SayLine(
                    caption.ifBlank {
                        when {
                            playing -> "${current?.title} 듣는 중이야"
                            featured == null -> "어떤 곡이 좋을지 골라 볼게"
                            else -> "지금은 ${featured.title} 어때?"
                        }
                    },
                    maxLines = 2,
                )
                if (featured != null) {
                    Spacer(Modifier.height(36.dp))
                    NowPlaying(
                        item = featured,
                        isCurrent = current != null,
                        playing = playing,
                        onPlayPause = {
                            when {
                                current == null -> play(featured)
                                paused -> onResume()
                                else -> onPause()
                            }
                        },
                        onNext = {
                            val i = items.indexOfFirst { it.id == featured.id }
                            items.getOrNull((i + 1) % items.size.coerceAtLeast(1))?.let { onPlayLocal(it.id) }
                        },
                    )
                }
            }
        }
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 124.dp, end = 32.dp, bottom = 32.dp)
                .width(440.dp)
                .fillMaxHeight()
                .panel(),
        ) {
            Text(
                if (items.isEmpty()) "곡" else "곡 ${items.size}개",
                style = MaterialTheme.typography.titleLarge,
                color = DoranPalette.InkSoft,
                modifier = Modifier.padding(start = 28.dp, top = 22.dp, bottom = 8.dp),
            )
            if (items.isEmpty()) {
                Text(
                    "기기에 넣어 둔 곡이 없어. 곡을 넣으면 여기 모여.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = DoranPalette.InkSoft,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                )
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                    if (index > 0) Rule(Modifier.padding(horizontal = 28.dp))
                    TrackRow(item, isCurrent = item.id == nowPlaying, playing = item.id == nowPlaying && !paused, onPlay = { play(item) })
                }
            }
        }
    }
}

/** 지금 곡. 곡 이름을 크게, 그 아래 아티스트와 분위기, 그 아래 재생·다음 */
@Composable
private fun NowPlaying(item: MusicItem, isCurrent: Boolean, playing: Boolean, onPlayPause: () -> Unit, onNext: () -> Unit) {
    Text(item.title, style = DoranText.display, color = DoranPalette.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(6.dp))
    val sub = listOf(item.artist, item.mood).filter(String::isNotBlank).joinToString(" · ")
    Text(
        when {
            !isCurrent && item.card != null -> listOf("도란이 고른 곡", sub).filter(String::isNotBlank).joinToString(" · ")
            else -> sub
        },
        style = DoranText.etaSide,
        color = DoranPalette.InkSoft,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(28.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        RoundButton(
            size = 104.dp,
            filled = true,
            label = if (playing) "일시정지" else "재생",
            onClick = onPlayPause,
        ) { Glyph(if (playing) DoranIcon.PAUSE else DoranIcon.PLAY, DoranPalette.OnAccent, size = 40.dp) }
        RoundButton(size = 80.dp, filled = false, label = "다음 곡", onClick = onNext) {
            Glyph(DoranIcon.NEXT, DoranPalette.Ink, size = 30.dp)
        }
        Spacer(Modifier.width(8.dp))
        if (playing) Bars(Modifier.size(40.dp))
        Text(
            when {
                playing -> "재생 중"
                isCurrent -> "멈춤"
                else -> "누르면 틀어 줄게"
            },
            style = MaterialTheme.typography.titleLarge,
            color = if (playing) DoranPalette.Accent else DoranPalette.InkSoft,
        )
    }
}

@Composable
private fun RoundButton(size: Dp, filled: Boolean, label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) DoranPalette.Accent else DoranPalette.Surface)
            .then(if (filled) Modifier else Modifier.border(BorderStroke(2.dp, DoranPalette.LineStrong), CircleShape))
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun TrackRow(item: MusicItem, isCurrent: Boolean, playing: Boolean, onPlay: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .clickable(onClick = onPlay)
            .padding(horizontal = 28.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            when {
                playing -> Bars(Modifier.size(28.dp))
                isCurrent -> Glyph(DoranIcon.PAUSE, DoranPalette.Accent, size = 22.dp)
                else -> Glyph(DoranIcon.PLAY, DoranPalette.InkMute, size = 22.dp)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleLarge,
                color = if (isCurrent) DoranPalette.Accent else DoranPalette.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val line = listOf(item.mood, item.artist).filter(String::isNotBlank).joinToString(" · ")
            if (line.isNotBlank()) Text(line, style = MaterialTheme.typography.bodyMedium, color = DoranPalette.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // 도란이 권한 곡 표시. 도란과 같은 주황 점
        if (item.card != null) {
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(12.dp).background(DoranPalette.Accent, CircleShape))
        }
    }
}

/** 재생 중 표시. 막대 네 개가 오르내린다 */
@Composable
private fun Bars(modifier: Modifier = Modifier, color: Color = DoranPalette.Accent) {
    val t by rememberInfiniteTransition(label = "bars").animateFloat(
        initialValue = 0f,
        targetValue = 6.283f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    Canvas(modifier) {
        val w = size.width / 7f
        for (i in 0 until 4) {
            val h = size.height * (0.3f + 0.7f * abs(sin(t + i * 1.1f)))
            drawRoundRect(color, topLeft = Offset(i * w * 1.8f, size.height - h), size = Size(w, h), cornerRadius = CornerRadius(w / 2f))
        }
    }
}
