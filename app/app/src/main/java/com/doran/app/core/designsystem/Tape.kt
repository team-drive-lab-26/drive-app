package com.doran.app.core.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.doran.app.core.model.TapeKind
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/** 테이프 위에 놓이는 것 하나. [time]은 화면에 그대로 적는 시각 */
data class TapeEntry(
    val id: String,
    val kind: TapeKind,
    val title: String,
    val time: String = "",
    val seconds: Int = 0,
    val atMillis: Long = 0L,
)

/** 테이프 띠 바탕. 녹음된 쪽은 짙게, [blankFrom](0~1)부터 오른쪽은 아직 비어 있는 테이프 */
private fun DrawScope.drawTapeBand(blankFrom: Float, bandHeight: Float) {
    val edge = 6.dp.toPx()
    val blankX = size.width * blankFrom.coerceIn(0f, 1f)
    drawRect(DoranPalette.Tape, size = Size(blankX, bandHeight))
    if (blankX < size.width) {
        drawRect(DoranPalette.TapeBlank, topLeft = Offset(blankX, 0f), size = Size(size.width - blankX, bandHeight))
        // 빈 테이프 가운데의 점선. 앞으로 채울 자리
        drawLine(
            DoranPalette.TapeEdge,
            Offset(blankX + 16.dp.toPx(), bandHeight / 2f),
            Offset(size.width, bandHeight / 2f),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 10.dp.toPx())),
        )
    }
    // 테이프 위아래 가장자리
    drawLine(DoranPalette.TapeEdge, Offset(0f, edge), Offset(size.width, edge), strokeWidth = 1.dp.toPx())
    drawLine(DoranPalette.TapeEdge, Offset(0f, bandHeight - edge), Offset(size.width, bandHeight - edge), strokeWidth = 1.dp.toPx())
}

/** 재생 헤드. 띠 위아래로 조금 삐져나온 귤색 막대 */
private fun DrawScope.drawHead(x: Float, bandHeight: Float) {
    val w = 6.dp.toPx()
    val over = 14.dp.toPx()
    drawRoundRect(
        DoranPalette.Head,
        topLeft = Offset(x - w / 2f, -over),
        size = Size(w, bandHeight + over * 2),
        cornerRadius = CornerRadius(w / 2f),
    )
}

/** 목소리 구간의 파형. 같은 목소리는 늘 같은 모양이 나오게 id로 높이를 정한다 */
@Composable
private fun Waveform(seed: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val step = 6.dp.toPx()
        val bar = 3.5.dp.toPx()
        val count = (size.width / step).toInt().coerceAtLeast(1)
        for (i in 0 until count) {
            val h = size.height * (0.22f + 0.78f * abs(sin((seed % 997 + 1) * 0.37f + i * 1.13f) * sin(i * 0.41f + seed * 0.07f)))
            drawRoundRect(
                color,
                topLeft = Offset(i * step, (size.height - h) / 2f),
                size = Size(bar, h),
                cornerRadius = CornerRadius(bar / 2f),
            )
        }
    }
}

/** 실제 마이크 크기 기록으로 그리는 파형. 녹음 화면에서 쓴다 */
private fun DrawScope.drawLevels(levels: List<Float>, capacity: Int, width: Float, centerY: Float, maxH: Float, color: Color) {
    if (capacity <= 0) return
    val step = width / capacity
    val bar = (step * 0.6f).coerceAtLeast(2f)
    levels.forEachIndexed { i, level ->
        val h = maxH * (0.08f + 0.92f * level.coerceIn(0f, 1f))
        drawRoundRect(color, topLeft = Offset(i * step, centerY - h / 2f), size = Size(bar, h), cornerRadius = CornerRadius(bar / 2f))
    }
}

/** 들른 곳 하나와 거기서 남긴 목소리들. 목소리는 장소 아래에 붙여 테이프 폭을 아낀다 */
private class TapeGroup(val head: TapeEntry?, val voices: List<TapeEntry>) {
    val id: String get() = head?.id ?: voices.first().id
    val atMillis: Long get() = head?.atMillis ?: voices.first().atMillis
}

private fun group(entries: List<TapeEntry>): List<TapeGroup> {
    val out = mutableListOf<TapeGroup>()
    var head: TapeEntry? = null
    var voices = mutableListOf<TapeEntry>()
    fun flush() {
        if (head != null || voices.isNotEmpty()) out += TapeGroup(head, voices)
        head = null
        voices = mutableListOf()
    }
    entries.forEach { entry ->
        if (entry.kind == TapeKind.VOICE) voices += entry else {
            flush()
            head = entry
        }
    }
    flush()
    return out
}

@Composable
private fun VoiceChip(entry: TapeEntry) {
    val color = FamilyRibbon.colorFor(entry.title)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Waveform(
            seed = entry.id.hashCode(),
            color = color,
            modifier = Modifier.width(maxOf(entry.seconds.coerceIn(3, 15) * 4, 30).dp).height(30.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(entry.title, style = DoranText.labelSmall, color = color, maxLines = 1)
    }
}

@Composable
private fun TapeItem(group: TapeGroup, bandHeight: Dp, selected: Boolean, onSelect: (() -> Unit)?) {
    val click = if (onSelect != null) Modifier.clickable(onClick = onSelect) else Modifier
    val head = group.head
    Row(click.height(bandHeight)) {
        if (head != null) {
            Box(
                Modifier
                    .padding(vertical = if (head.kind == TapeKind.START) 28.dp else 14.dp)
                    .width(if (selected) 6.dp else 3.dp)
                    .fillMaxHeight()
                    .background(if (selected) DoranPalette.Head else DoranPalette.TapeText),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(
            Modifier.fillMaxHeight().padding(vertical = 18.dp).widthIn(max = 300.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                head?.title ?: "",
                style = DoranText.label,
                color = DoranPalette.TapeText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                group.voices.take(3).forEach { VoiceChip(it) }
            }
            Text(head?.time ?: "", style = DoranText.time, color = DoranPalette.TapeTextSoft)
        }
    }
}

/**
 * 여행 테이프. 왼쪽부터 지나온 곳(남긴 목소리는 그 아래)이 시간 순서로 놓이고, [playhead](0~1) 오른쪽은 빈 테이프다.
 * 빈 테이프 끝에 목적지([endTitle])와 남은 시간을 적는다. 항목이 많으면 오래된 것부터 왼쪽으로 밀려난다.
 * [onSelect]를 주면 항목을 눌러 고를 수 있다(앨범 지도와 맞물릴 때).
 */
@Composable
fun TapeStrip(
    entries: List<TapeEntry>,
    playhead: Float,
    modifier: Modifier = Modifier,
    bandHeight: Dp = 156.dp,
    nowLabel: String = "",
    endTitle: String = "",
    endSub: String = "",
    showHead: Boolean = true,
    selectedId: String? = null,
    onSelect: ((String) -> Unit)? = null,
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth().height(bandHeight + 44.dp)) {
        val widthPx = constraints.maxWidth.toFloat()
        val bandPx = with(density) { bandHeight.toPx() }
        val headX = widthPx * playhead.coerceIn(0f, 1f)
        Canvas(Modifier.fillMaxSize()) {
            drawTapeBand(if (showHead) playhead else 1f, bandPx)
            if (showHead) drawHead(headX, bandPx)
        }

        val gap = with(density) { 20.dp.roundToPx() }
        val left = with(density) { 28.dp.roundToPx() }
        val right = (if (showHead) headX else widthPx).roundToInt() - with(density) { 28.dp.roundToPx() }
        val groups = remember(entries) { group(entries) }
        val first = groups.minOfOrNull { it.atMillis } ?: 0L
        val last = groups.maxOfOrNull { it.atMillis } ?: 0L
        Layout(
            content = {
                groups.forEach { g ->
                    TapeItem(g, bandHeight, g.id == selectedId, onSelect?.let { select -> { select(g.id) } })
                }
            },
            modifier = Modifier.height(bandHeight).fillMaxWidth(),
        ) { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
            val span = (right - left).coerceAtLeast(1)
            // 시간 비율대로 놓고, 겹치면 오른쪽으로 민다
            val xs = IntArray(placeables.size)
            groups.forEachIndexed { i, g ->
                val t = if (last > first) (g.atMillis - first).toFloat() / (last - first) else 0f
                val desired = left + (t * span * 0.86f).roundToInt()
                xs[i] = if (i == 0) desired else maxOf(desired, xs[i - 1] + placeables[i - 1].width + gap)
            }
            // 헤드를 넘으면 뒤에서부터 왼쪽으로 당긴다. 그래도 왼쪽 끝을 넘는 오래된 항목은 숨긴다
            for (i in placeables.indices.reversed()) {
                val limit = if (i == placeables.lastIndex) right - placeables[i].width else xs[i + 1] - gap - placeables[i].width
                xs[i] = minOf(xs[i], limit)
            }
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeables.forEachIndexed { i, p -> if (xs[i] >= left - 1) p.place(xs[i], 0) }
            }
        }

        if (endTitle.isNotBlank() || endSub.isNotBlank()) {
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .height(bandHeight)
                    .padding(end = 32.dp, top = 22.dp, bottom = 22.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(endTitle, style = DoranText.label, color = DoranPalette.TapeText, maxLines = 1)
                Text(endSub, style = DoranText.time, color = DoranPalette.TapeTextSoft)
            }
        }

        if (showHead && nowLabel.isNotBlank()) {
            Text(
                nowLabel,
                style = DoranText.time,
                color = DoranPalette.Ink,
                modifier = Modifier.offset { IntOffset((headX + with(density) { 12.dp.toPx() }).roundToInt(), (bandPx + with(density) { 16.dp.toPx() }).roundToInt()) },
            )
        }
    }
}

/**
 * 주행 중 자막 띠. 화면을 가로지르는 테이프에 한 줄만 적는다.
 * 왼쪽 [leading]만큼은 도란이 앉을 자리로 비운다.
 */
@Composable
fun CaptionBand(caption: String, hint: String, modifier: Modifier = Modifier, height: Dp = 176.dp, leading: Dp = 0.dp, trailing: Dp = 48.dp) {
    Box(modifier.fillMaxWidth().height(height)) {
        Canvas(Modifier.fillMaxSize()) { drawTapeBand(1f, size.height) }
        AnimatedContent(
            targetState = caption,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            modifier = Modifier.align(Alignment.CenterStart).padding(start = leading, end = trailing),
            label = "caption",
        ) { text ->
            Text(
                text.ifBlank { hint },
                style = MaterialTheme.typography.displayMedium,
                color = if (text.isBlank()) DoranPalette.TapeTextSoft else DoranPalette.TapeText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 녹음 테이프. 왼쪽부터 마이크 크기가 파형으로 적히고, [progress](0~1)에 쓰기 헤드가 있다.
 * [capacity]는 꽉 찼을 때의 막대 수, [label]은 남긴 사람을 고른 뒤 펜으로 적는 이름.
 */
@Composable
fun RecordTape(
    levels: List<Float>,
    capacity: Int,
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    bandHeight: Dp = 220.dp,
    label: String = "",
) {
    Box(modifier.fillMaxWidth().height(bandHeight)) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = 40.dp.toPx()
            val usable = size.width - inset * 2
            val headX = inset + usable * progress.coerceIn(0f, 1f)
            drawTapeBand(headX / size.width, size.height)
            translate(left = inset) {
                drawLevels(levels, capacity, usable, size.height / 2f, size.height * 0.5f, color)
            }
            drawHead(headX, size.height)
        }
        if (label.isNotBlank()) {
            Text(
                label,
                style = DoranText.label,
                color = color,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 40.dp, bottom = 16.dp),
            )
        }
    }
}

/** 띠 위에 앉은 도란의 왼쪽 위치. 캐릭터 그림의 발이 띠 윗변에 닿게 맞춘다 */
fun doranFeetOffset(size: Dp): Dp = size * 0.84f

@Composable
fun TapeSwatch(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(20.dp).background(color, DoranButtonShape))
}

private val HHMM = java.time.format.DateTimeFormatter.ofPattern("HH:mm")

fun clockText(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.of("Asia/Seoul")).format(HHMM)

fun com.doran.app.core.model.TapeMark.toEntry(): TapeEntry =
    TapeEntry(id = id, kind = kind, title = label, time = clockText(atMillis), seconds = seconds, atMillis = atMillis)
