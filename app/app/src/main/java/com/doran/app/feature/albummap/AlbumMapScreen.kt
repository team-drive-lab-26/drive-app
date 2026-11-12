package com.doran.app.feature.albummap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.doran.app.core.designsystem.BackBar
import com.doran.app.core.designsystem.DoranPalette
import com.doran.app.core.designsystem.DoranText
import com.doran.app.core.designsystem.FamilyRibbon
import com.doran.app.core.designsystem.PersonDot
import com.doran.app.core.designsystem.Rule
import com.doran.app.core.designsystem.Suit
import kotlin.math.cos
import kotlin.math.hypot

/**
 * 3D 지도 앨범. 다녀온 길을 비스듬히 눕힌 지도 위에 주황 경로선으로 그리고, 들른 곳마다 핀을 세운다.
 * 아래에는 들른 순서대로 지나온 길 막대가 있고, 지도 핀이나 막대 위 점 어느 쪽을 눌러도 같은 곳이 골라진다.
 * 목소리를 남긴 핀은 남긴 사람 색이고, 고르면 오른쪽 칸에서 그 목소리를 다시 듣는다.
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

    Column(Modifier.fillMaxSize().background(DoranPalette.Background)) {
        BackBar(back = "지나온 길", title = ui.title, onBack = onBack)
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
                        val color = pin.voices.firstOrNull()?.let { FamilyRibbon.colorFor(it.speaker) } ?: DoranPalette.Ink
                        drawOval(Color.Black.copy(alpha = 0.35f), topLeft = Offset(ground.x - 14f * scale, ground.y - 5f * scale), size = Size(28f * scale, 10f * scale))
                        drawLine(DoranPalette.InkSoft, ground, head, strokeWidth = 4f * scale, cap = StrokeCap.Round)
                        val radius = (if (selected) 24f else 17f) * scale
                        if (selected) drawCircle(DoranPalette.Accent, radius + 8f * scale, head)
                        drawCircle(color, radius, head)
                        val label = measurer.measure(pin.name, TextStyle(fontFamily = Suit, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (selected) DoranPalette.OnAccent else DoranPalette.Ink))
                        // 이름표. 경로선 위에 겹쳐도 읽히게 바탕을 깐다
                        val padX = 10.dp.toPx()
                        val padY = 4.dp.toPx()
                        val tl = Offset(head.x - label.size.width / 2f - padX, head.y - radius - label.size.height - padY * 2 - 8.dp.toPx())
                        drawRoundRect(
                            if (selected) DoranPalette.Accent else DoranPalette.Surface.copy(alpha = 0.94f),
                            topLeft = tl,
                            size = Size(label.size.width + padX * 2, label.size.height + padY * 2),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                        )
                        drawText(label, topLeft = Offset(tl.x + padX, tl.y + padY))
                    }
                    projection.endLabels(ui.route).forEach { (text, point) ->
                        val p = projection.ground(point.first, point.second, size.width, size.height, k)
                        val label = measurer.measure(text, TextStyle(fontFamily = Suit, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DoranPalette.InkSoft))
                        val padX = 8.dp.toPx()
                        val tl = Offset(p.x - label.size.width / 2f - padX, p.y + 14.dp.toPx())
                        drawRoundRect(DoranPalette.Background.copy(alpha = 0.9f), topLeft = tl, size = Size(label.size.width + padX * 2, label.size.height + 4.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
                        drawText(label, topLeft = Offset(tl.x + padX, tl.y + 2.dp.toPx()))
                    }
                }
                if (ui.message.isNotBlank()) {
                    Text(ui.message, style = MaterialTheme.typography.bodyLarge, color = DoranPalette.InkSoft, modifier = Modifier.padding(start = 40.dp, top = 4.dp))
                }
            }
            // 고른 곳 칸은 지도 옆 자리를 따로 둬서 지도 끝(도착)을 가리지 않는다
            Column(Modifier.width(460.dp).fillMaxHeight().padding(end = 40.dp, top = 8.dp, bottom = 24.dp)) {
                val pin = ui.selected
                if (pin != null) {
                    PinPanel(pin = pin, playingId = playing, onToggle = viewModel::toggle, modifier = Modifier.fillMaxWidth())
                } else if (!ui.loading && ui.pins.isNotEmpty()) {
                    Text("지도 핀이나 아래 막대의 점을 누르면 그곳에서 남긴 목소리를 들을 수 있어.", style = MaterialTheme.typography.bodyLarge, color = DoranPalette.InkSoft)
                }
            }
        }
        StopTimeline(ui.pins, ui.selectedId, onSelect = viewModel::select)
    }
}

/** 지나온 길 막대. 들른 순서대로 점을 찍고, 목소리를 남긴 곳은 그 사람 색 */
@Composable
private fun StopTimeline(pins: List<MapPin>, selectedId: String?, onSelect: (String) -> Unit) {
    if (pins.isEmpty()) return
    Box(Modifier.fillMaxWidth().height(132.dp).padding(horizontal = 40.dp)) {
        // 점들을 꿰는 선. 점 가운데 높이에 맞춘다
        Canvas(Modifier.align(Alignment.TopStart).padding(start = 60.dp, end = 60.dp, top = 11.dp).fillMaxWidth().height(4.dp)) {
            drawLine(DoranPalette.LineStrong, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        }
        Row(Modifier.fillMaxWidth().padding(top = 0.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            pins.forEach { pin ->
                val selected = pin.id == selectedId
                Column(
                    Modifier
                        .heightIn(min = 112.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelect(pin.id) }
                        .padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .padding(top = 0.dp)
                            .size(if (selected) 26.dp else 20.dp)
                            .background(if (selected) DoranPalette.Accent else DoranPalette.Background, CircleShape)
                            .border(3.dp, if (selected) DoranPalette.Accent else DoranPalette.InkSoft, CircleShape),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(pin.name, style = MaterialTheme.typography.titleMedium, color = if (selected) DoranPalette.Ink else DoranPalette.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(220.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        pin.voices.forEach { PersonDot(it.speaker, Modifier.size(12.dp)) }
                        if (pin.time.isNotBlank()) Text(pin.time, style = DoranText.number, color = DoranPalette.InkMute)
                    }
                }
            }
        }
    }
}

@Composable
private fun PinPanel(pin: MapPin, playingId: String?, onToggle: (Voice) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .clip(shape)
            .background(DoranPalette.Surface)
            .border(1.dp, DoranPalette.Line, shape)
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Text(pin.name, style = MaterialTheme.typography.headlineMedium, color = DoranPalette.Ink)
        if (pin.time.isNotBlank()) Text("${pin.time} 도착", style = DoranText.number, color = DoranPalette.InkSoft)
        Spacer(Modifier.height(12.dp))
        if (pin.voices.isEmpty()) {
            Text("여기서는 목소리를 남기지 않았어.", style = MaterialTheme.typography.bodyMedium, color = DoranPalette.InkSoft)
        }
        pin.voices.forEachIndexed { index, voice ->
            if (index > 0) Rule()
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .clickable(enabled = voice.available) { onToggle(voice) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PersonDot(voice.speaker, Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(voice.speaker, style = MaterialTheme.typography.titleLarge, color = DoranPalette.Ink)
                    Text(if (voice.available) "${voice.seconds}초" else "다른 기기에서 남긴 목소리라 여기선 못 들어", style = MaterialTheme.typography.bodyMedium, color = DoranPalette.InkSoft)
                }
                if (voice.available) {
                    Text(if (playingId == voice.capsuleId) "멈추기" else "들어보기", style = MaterialTheme.typography.labelLarge, color = DoranPalette.Accent)
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
        val bottom = height * 0.80f
        val y = bottom - (1f - s) / (1f - far) * (bottom - top)
        return Offset(width / 2f + x * width * 0.34f * s, y)
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
    drawPath(path, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(DoranPalette.RoadFar, DoranPalette.RoadNear), startY = corners[2].y, endY = corners[0].y))
    drawPath(path, DoranPalette.LineStrong, style = Stroke(width = 2f))
    // 지도 바닥의 옅은 격자. 기울어진 정도가 보이게
    for (i in 1 until 6) {
        val z = -0.1f + 1.2f * i / 6f
        drawLine(DoranPalette.Line, projection.point(-1.1f, z, size.width, size.height, tilt), projection.point(1.1f, z, size.width, size.height, tilt), strokeWidth = 1f)
        val x = -1.1f + 2.2f * i / 6f
        drawLine(DoranPalette.Line, projection.point(x, -0.1f, size.width, size.height, tilt), projection.point(x, 1.1f, size.width, size.height, tilt), strokeWidth = 1f)
    }
}

private fun DrawScope.drawRoute(projection: MapProjection, route: List<Pair<Double, Double>>, tilt: Float) {
    if (route.size < 2) return
    val points = route.map { (lat, lon) -> projection.ground(lat, lon, size.width, size.height, tilt) }
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(path, Color.Black.copy(alpha = 0.4f), style = Stroke(width = 16f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, DoranPalette.Accent, style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
