package com.doran.app.feature.albummap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.doran.app.core.designsystem.BackBar
import com.doran.app.core.designsystem.DoranPalette
import com.doran.app.core.designsystem.DoranText
import com.doran.app.core.designsystem.FamilyRibbon
import com.doran.app.core.designsystem.PenHand
import com.doran.app.core.designsystem.Rule
import com.doran.app.core.designsystem.Suit
import com.doran.app.core.designsystem.TapeEntry
import com.doran.app.core.designsystem.TapeStrip
import com.doran.app.core.designsystem.TapeSwatch
import com.doran.app.core.model.TapeKind
import kotlin.math.cos
import kotlin.math.hypot

/**
 * 3D 지도 앨범. 다녀온 길을 비스듬히 눕힌 종이 지도 위에 테이프 색 띠로 그리고, 들른 곳마다 핀을 세운다.
 * 아래에는 같은 여행이 테이프로 펼쳐진다. 지도 핀이나 테이프 구간 어느 쪽을 눌러도 같은 곳이 골라진다.
 * 목소리를 남긴 곳의 핀은 남긴 사람 색이고, 고르면 오른쪽 위 칸에서 그 목소리를 다시 듣는다.
 * 열 때 지도가 위에서 내려다보는 각도에서 비스듬한 각도로 눕는다.
 */
@Composable
fun AlbumMapScreen(onBack: () -> Unit, viewModel: AlbumMapViewModel = viewModel(factory = AlbumMapViewModel.Factory)) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val playing by viewModel.playingId.collectAsStateWithLifecycle()
    val tilt = remember { Animatable(0.15f) }
    LaunchedEffect(ui.loading) {
        if (!ui.loading) tilt.animateTo(1.35f, tween(1400, easing = FastOutSlowInEasing))
    }
    val measurer = rememberTextMeasurer()
    val projection = remember(ui.route, ui.pins) { MapProjection.of(ui.route, ui.pins) }
    val entries = remember(ui.pins) {
        ui.pins.flatMapIndexed { index, pin ->
            listOf(TapeEntry(pin.id, TapeKind.STOP, pin.name, time = pin.time, atMillis = index * 10L)) +
                pin.voices.mapIndexed { j, voice ->
                    TapeEntry("${pin.id}#${voice.capsuleId}", TapeKind.VOICE, voice.speaker, seconds = voice.seconds, atMillis = index * 10L + j + 1)
                }
        }
    }

    Column(Modifier.fillMaxSize().background(DoranPalette.Background)) {
        BackBar(back = "여행 테이프", title = ui.title, onBack = onBack)
        Row(Modifier.weight(1f).fillMaxWidth()) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(ui.pins, projection) {
                        detectTapGestures { tap ->
                            val hit = ui.pins.minByOrNull { pin ->
                                val head = projection.pinHead(pin, size.width.toFloat(), size.height.toFloat(), tilt.value)
                                hypot(head.x - tap.x, head.y - tap.y)
                            } ?: return@detectTapGestures
                            val head = projection.pinHead(hit, size.width.toFloat(), size.height.toFloat(), tilt.value)
                            if (hypot(head.x - tap.x, head.y - tap.y) < 90f) viewModel.select(hit.id)
                        }
                    },
            ) {
                val k = tilt.value
                drawGround(projection, k)
                drawRoute(projection, ui.route, k)
                // 먼 핀부터 그려 가까운 핀이 위에 오게 한다
                ui.pins.sortedByDescending { projection.depth(it.latitude) }.forEach { pin ->
                    val ground = projection.ground(pin.latitude, pin.longitude, size.width, size.height, k)
                    val head = projection.pinHead(pin, size.width, size.height, k)
                    val scale = projection.scale(projection.depth(pin.latitude), k)
                    val selected = pin.id == ui.selectedId
                    val color = pin.voices.firstOrNull()?.let { FamilyRibbon.colorFor(it.speaker) } ?: DoranPalette.Surface
                    drawOval(
                        Color.Black.copy(alpha = 0.14f),
                        topLeft = Offset(ground.x - 14f * scale, ground.y - 5f * scale),
                        size = Size(28f * scale, 10f * scale),
                    )
                    drawLine(DoranPalette.Ink, ground, head, strokeWidth = 4f * scale, cap = StrokeCap.Round)
                    val radius = (if (selected) 24f else 17f) * scale
                    if (selected) drawCircle(DoranPalette.Head, radius + 8f * scale, head)
                    drawCircle(color, radius, head)
                    drawCircle(DoranPalette.Ink, radius, head, style = Stroke(width = 3f * scale))
                    val label = measurer.measure(pin.name, TextStyle(fontFamily = PenHand, fontSize = 24.sp, color = DoranPalette.Ink))
                    drawText(label, topLeft = Offset(head.x - label.size.width / 2f, head.y - radius - label.size.height - 6f))
                }
                projection.endLabels(ui.route).forEach { (text, point) ->
                    val p = projection.ground(point.first, point.second, size.width, size.height, k)
                    val label = measurer.measure(
                        text,
                        TextStyle(fontFamily = Suit, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = DoranPalette.InkSoft),
                    )
                    drawText(label, topLeft = Offset(p.x - label.size.width / 2f, p.y + 34f))
                }
            }

            if (ui.message.isNotBlank()) {
                Text(
                    ui.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = DoranPalette.InkSoft,
                    modifier = Modifier.padding(start = 40.dp, top = 4.dp),
                )
            }

        }
            // 고른 곳 칸은 지도 옆 자리를 따로 둬서 지도 끝(도착)을 가리지 않는다
            Column(Modifier.width(460.dp).fillMaxHeight().padding(end = 40.dp, top = 8.dp, bottom = 24.dp)) {
                val pin = ui.selected
                if (pin != null) {
                    PinPanel(pin = pin, playingId = playing, onToggle = viewModel::toggle, modifier = Modifier.fillMaxWidth())
                } else if (!ui.loading && ui.pins.isNotEmpty()) {
                    Text(
                        "지도 핀이나 아래 테이프를 누르면 그곳에서 남긴 목소리를 들을 수 있어.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = DoranPalette.InkSoft,
                    )
                }
            }
        }
        TapeStrip(
            entries = entries,
            playhead = 1f,
            showHead = false,
            bandHeight = 140.dp,
            selectedId = ui.selectedId,
            onSelect = { id -> viewModel.select(id.substringBefore('#')) },
        )
    }
}

@Composable
private fun PinPanel(pin: MapPin, playingId: String?, onToggle: (Voice) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .shadow(10.dp, shape, ambientColor = DoranPalette.Ink, spotColor = DoranPalette.Ink)
            .clip(shape)
            .background(DoranPalette.Surface)
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Text(pin.name, style = MaterialTheme.typography.headlineMedium, color = DoranPalette.Ink)
        if (pin.time.isNotBlank()) Text("${pin.time} 도착", style = DoranText.time, color = DoranPalette.InkSoft)
        Spacer(Modifier.height(12.dp))
        if (pin.voices.isEmpty()) {
            Text("여기서는 목소리를 남기지 않았어.", style = MaterialTheme.typography.bodyMedium, color = DoranPalette.InkSoft)
        }
        pin.voices.forEachIndexed { index, voice ->
            if (index > 0) Rule(color = DoranPalette.LineOnPaper)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .clickable(enabled = voice.available) { onToggle(voice) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TapeSwatch(FamilyRibbon.colorFor(voice.speaker), Modifier.size(28.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(voice.speaker, style = MaterialTheme.typography.titleLarge, color = DoranPalette.Ink)
                    Text(
                        if (voice.available) "${voice.seconds}초" else "이 기기에 파일이 없어",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DoranPalette.InkSoft,
                    )
                }
                if (voice.available) {
                    Text(
                        if (playingId == voice.capsuleId) "멈추기" else "들어보기",
                        style = MaterialTheme.typography.labelLarge,
                        color = DoranPalette.Ink,
                    )
                }
            }
        }
    }
}

/** 위도·경도를 비스듬히 눕힌 지도 평면으로 옮긴다. 가까운 쪽이 화면 아래, 북쪽이 화면 위 */
internal class MapProjection(
    private val minLat: Double,
    private val maxLat: Double,
    private val minLon: Double,
    private val maxLon: Double,
) {
    fun depth(lat: Double): Float = (((lat - minLat) / (maxLat - minLat)).toFloat()).coerceIn(-0.2f, 1.2f)

    private fun across(lon: Double): Float = (((lon - minLon) / (maxLon - minLon)) * 2 - 1).toFloat()

    /** 멀수록 작아지는 비율. [tilt]가 클수록 더 눕는다 */
    fun scale(depth: Float, tilt: Float): Float = 1f / (1f + tilt * depth)

    fun ground(lat: Double, lon: Double, width: Float, height: Float, tilt: Float): Offset =
        point(across(lon), depth(lat), width, height, tilt)

    fun point(x: Float, z: Float, width: Float, height: Float, tilt: Float): Offset {
        val s = scale(z, tilt)
        val far = scale(1f, tilt)
        val top = height * 0.30f
        val bottom = height * 0.88f
        val y = bottom - (1f - s) / (1f - far) * (bottom - top)
        return Offset(width / 2f + x * width * 0.40f * s, y)
    }

    fun pinHead(pin: MapPin, width: Float, height: Float, tilt: Float): Offset {
        val ground = ground(pin.latitude, pin.longitude, width, height, tilt)
        return Offset(ground.x, ground.y - 90f * scale(depth(pin.latitude), tilt))
    }

    fun endLabels(route: List<Pair<Double, Double>>): List<Pair<String, Pair<Double, Double>>> =
        if (route.size < 2) emptyList() else listOf("출발" to route.first(), "도착" to route.last())

    companion object {
        fun of(route: List<Pair<Double, Double>>, pins: List<MapPin>): MapProjection {
            val lats = route.map { it.first } + pins.map { it.latitude }
            val lons = route.map { it.second } + pins.map { it.longitude }
            if (lats.isEmpty()) return MapProjection(37.0, 38.0, 127.0, 129.0)
            // 경도 1도는 위도보다 짧으므로 위도 범위를 맞춰 지도 비율을 지킨다
            val midLat = (lats.min() + lats.max()) / 2
            val lonSpan = (lons.max() - lons.min()).coerceAtLeast(0.02)
            val latSpan = maxOf(lats.max() - lats.min(), lonSpan * cos(Math.toRadians(midLat)) * 0.45)
            val padLat = latSpan * 0.15
            val padLon = lonSpan * 0.08
            return MapProjection(
                midLat - latSpan / 2 - padLat,
                midLat + latSpan / 2 + padLat,
                lons.min() - padLon,
                lons.max() + padLon,
            )
        }
    }
}

private fun DrawScope.drawGround(projection: MapProjection, tilt: Float) {
    val corners = listOf(-1.1f to -0.1f, 1.1f to -0.1f, 1.1f to 1.1f, -1.1f to 1.1f)
        .map { (x, z) -> projection.point(x, z, size.width, size.height, tilt) }
    val path = Path().apply {
        moveTo(corners[0].x, corners[0].y)
        corners.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    drawPath(path, Color(0xFFEAF3F8))
    drawPath(path, DoranPalette.Line, style = Stroke(width = 3f))
}

private fun DrawScope.drawRoute(projection: MapProjection, route: List<Pair<Double, Double>>, tilt: Float) {
    if (route.size < 2) return
    val points = route.map { (lat, lon) -> projection.ground(lat, lon, size.width, size.height, tilt) }
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    // 길 아래 그림자를 먼저 깔아 지도 위로 살짝 떠 보이게 하고, 길은 테이프 색 띠로 그린다
    drawPath(path, Color.Black.copy(alpha = 0.10f), style = Stroke(width = 22f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, DoranPalette.Tape, style = Stroke(width = 12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
