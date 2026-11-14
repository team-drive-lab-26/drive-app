package com.doran.app.feature.albummap

import android.annotation.SuppressLint
import android.provider.Settings
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.RenderProcessGoneDetail
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.doran.app.BuildConfig
import com.doran.app.core.designsystem.BackBar
import com.doran.app.core.designsystem.DoranPalette
import com.doran.app.core.designsystem.DoranText
import com.doran.app.core.designsystem.FamilyRibbon
import com.doran.app.core.designsystem.PersonDot
import com.doran.app.core.designsystem.Suit
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 3D 지도 앨범. 왼쪽에 들른 순서대로 곳과 남긴 목소리를, 오른쪽에 실제 지형 위 다녀온 길을 보여 준다.
 * 왼쪽 목록이나 지도 핀 어느 쪽을 눌러도 같은 곳이 골라지고, 목소리는 목록에서 다시 듣는다.
 * 지도는 WebView의 MapLibre(assets/albummap/map.html)로 그리고, 인터넷이 없거나 지도를 못 띄우면 기기에서 그린 평면 지도로 바꾼다.
 */
@Composable
fun AlbumMapScreen(onBack: () -> Unit, viewModel: AlbumMapViewModel = viewModel(factory = AlbumMapViewModel.Factory)) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val playing by viewModel.playingId.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 디버그 빌드에서 `adb shell settings put global doran_flat_map 1`이면 평면 지도로 연다.
    // GPU 없는 CI 에뮬레이터는 WebGL 지도를 하얗게 그려서 시연 영상 녹화 때 쓴다
    var webFailed by remember {
        mutableStateOf(BuildConfig.DEBUG && Settings.Global.getString(context.contentResolver, "doran_flat_map") == "1")
    }

    Column(Modifier.fillMaxSize().background(DoranPalette.Background)) {
        BackBar(back = "지나온 길", title = ui.title, onBack = onBack)
        Row(Modifier.weight(1f).fillMaxWidth().padding(start = 40.dp, end = 40.dp, bottom = 28.dp)) {
            StopList(ui, playing, onSelect = viewModel::select, onToggle = viewModel::toggle, modifier = Modifier.width(520.dp).fillMaxHeight())
            Spacer(Modifier.width(28.dp))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, DoranPalette.Line, RoundedCornerShape(24.dp)),
            ) {
                if (webFailed) {
                    FlatMap(ui, onSelect = viewModel::select, modifier = Modifier.fillMaxSize())
                } else {
                    WebMap(ui, onSelect = viewModel::select, onFailed = { webFailed = true }, modifier = Modifier.fillMaxSize())
                }
                if (ui.message.isNotBlank()) {
                    Text(
                        ui.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = DoranPalette.Ink,
                        modifier = Modifier.padding(24.dp).clip(RoundedCornerShape(12.dp)).background(DoranPalette.Surface).padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

/** 왼쪽 칸. 여행 요약과 들른 곳 목록, 곳마다 남긴 목소리 */
@Composable
private fun StopList(
    ui: AlbumMapUiState,
    playingId: String?,
    onSelect: (String) -> Unit,
    onToggle: (Voice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(ui.selectedId) {
        val index = ui.pins.indexOfFirst { it.id == ui.selectedId }
        if (index >= 0) listState.animateScrollToItem(index + 1)
    }
    LazyColumn(modifier, state = listState) {
        item {
            val voiceCount = ui.pins.sumOf { it.voices.size }
            val km = routeKm(ui.route)
            Row(Modifier.padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (km > 0) SummaryChip("${km}km")
                if (ui.pins.isNotEmpty()) {
                    SummaryChip("${ui.pins.size}곳")
                    SummaryChip("목소리 ${voiceCount}개")
                }
            }
        }
        // 아직 들른 곳이 없으면 왼쪽 칸이 비어 보이지 않게 무엇이 쌓이는지 알려 준다
        if (ui.pins.isEmpty() && !ui.loading) {
            item {
                Text(
                    "차를 세운 곳과 그곳에서 남긴 목소리가 여기에 차례로 쌓여.",
                    style = MaterialTheme.typography.titleLarge,
                    color = DoranPalette.InkSoft,
                    modifier = Modifier.padding(top = 12.dp, end = 24.dp),
                )
            }
        }
        itemsIndexed(ui.pins, key = { _, pin -> pin.id }) { index, pin ->
            StopRow(index + 1, pin, selected = pin.id == ui.selectedId, last = index == ui.pins.lastIndex, playingId = playingId, onSelect = onSelect, onToggle = onToggle)
        }
    }
}

@Composable
private fun SummaryChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = DoranPalette.Ink,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(DoranPalette.Surface).border(1.dp, DoranPalette.Line, RoundedCornerShape(50)).padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

@Composable
private fun StopRow(
    number: Int,
    pin: MapPin,
    selected: Boolean,
    last: Boolean,
    playingId: String?,
    onSelect: (String) -> Unit,
    onToggle: (Voice) -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) DoranPalette.Accent.copy(alpha = 0.14f) else Color.Transparent)
            .clickable { onSelect(pin.id) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        // 번호 원. 지도 핀과 같은 번호다
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(if (pin.voices.isNotEmpty()) DoranPalette.Accent else DoranPalette.Surface, CircleShape)
                    .border(3.dp, DoranPalette.Accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("$number", style = MaterialTheme.typography.titleMedium, color = if (pin.voices.isNotEmpty()) DoranPalette.OnAccent else DoranPalette.Accent, textAlign = TextAlign.Center)
            }
            if (!last) Box(Modifier.padding(top = 6.dp).width(2.dp).height(28.dp).background(DoranPalette.AccentSoft))
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(pin.name, style = MaterialTheme.typography.headlineSmall, color = DoranPalette.Ink)
            if (pin.time.isNotBlank()) Text("${pin.time} 도착", style = DoranText.number, color = DoranPalette.InkSoft)
            pin.voices.forEach { voice ->
                Spacer(Modifier.height(10.dp))
                VoicePill(voice, playing = playingId == voice.capsuleId, onToggle = onToggle)
            }
        }
    }
}

/** 남긴 사람과 길이. 누르면 다시 듣는다 */
@Composable
private fun VoicePill(voice: Voice, playing: Boolean, onToggle: (Voice) -> Unit) {
    Row(
        Modifier
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(50))
            .background(DoranPalette.Surface)
            .border(1.dp, DoranPalette.Line, RoundedCornerShape(50))
            .clickable(enabled = voice.available) { onToggle(voice) }
            .padding(start = 10.dp, end = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).background(if (voice.available) DoranPalette.Accent else DoranPalette.LineStrong, CircleShape), contentAlignment = Alignment.Center) {
            Text(if (playing) "■" else "▶", color = DoranPalette.OnAccent, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.width(12.dp))
        PersonDot(voice.speaker, Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        Text(voice.speaker, style = MaterialTheme.typography.titleMedium, color = DoranPalette.Ink)
        Spacer(Modifier.width(10.dp))
        Text(
            if (voice.available) "0:%02d".format(voice.seconds) else "다른 기기에서 남김",
            style = DoranText.number,
            color = DoranPalette.InkSoft,
        )
    }
}

/** 실제 지형 지도. 페이지가 준비되면 기록을 넘기고, 핀을 누르면 [onSelect]를 부른다 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebMap(ui: AlbumMapUiState, onSelect: (String) -> Unit, onFailed: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // JS 다리 함수 이름(select, failed)과 겹치지 않게 이름을 달리 둔다
    val pick by rememberUpdatedState(onSelect)
    val giveUp by rememberUpdatedState(onFailed)
    var ready by remember { mutableStateOf(false) }
    val webView = remember {
        val html = context.assets.open("albummap/map.html").bufferedReader().use { it.readText() }
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            setBackgroundColor(DoranPalette.Surface.toArgb())
            // 지도 페이지 오류를 logcat(DoranMap)에서 보이게 한다
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    Log.d("DoranMap", "${message.message()} (${message.lineNumber()})")
                    return true
                }
            }
            // 지도 그리기 프로세스가 죽으면(그래픽 메모리 부족 등) 앱까지 끝나지 않게 받아서 평면 지도로 바꾼다
            webViewClient = object : WebViewClient() {
                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    Log.w("DoranMap", "지도 그리기 중단, 평면 지도로 바꿈 (crash=${detail.didCrash()})")
                    post { giveUp() }
                    return true
                }
            }
            addJavascriptInterface(
                object {
                    @JavascriptInterface fun ready() = post { ready = true }
                    @JavascriptInterface fun select(id: String) = post { pick(id) }
                    @JavascriptInterface fun failed() = post { giveUp() }
                },
                "Doran",
            )
            // 외부 지도 서버가 막지 않도록 https 주소를 바탕으로 연다
            loadDataWithBaseURL("https://doran.app/albummap/", html, "text/html", "utf-8", null)
        }
    }
    DisposableEffect(webView) { onDispose { webView.destroy() } }
    // 인터넷이 없으면 지도가 끝내 안 뜨므로 기다리다 평면 지도로 바꾼다
    LaunchedEffect(Unit) {
        delay(WEB_MAP_TIMEOUT_MS)
        if (!ready) giveUp()
    }
    LaunchedEffect(ready, ui.route, ui.pins) {
        if (ready && !ui.loading) webView.evaluateJavascript("show(${mapJson(ui)})", null)
    }
    LaunchedEffect(ready, ui.selectedId) {
        val id = ui.selectedId ?: return@LaunchedEffect
        if (ready) webView.evaluateJavascript("select(${JSONObject.quote(id)})", null)
    }
    AndroidView(factory = { webView }, modifier = modifier)
}

private const val WEB_MAP_TIMEOUT_MS = 12_000L

private fun mapJson(ui: AlbumMapUiState): String = JSONObject().apply {
    put("accent", "#%06X".format(0xFFFFFF and DoranPalette.Accent.toArgb()))
    put("route", JSONArray(ui.route.map { (lat, lon) -> JSONArray(listOf(lon, lat)) }))
    put("pins", JSONArray(ui.pins.map { pin ->
        JSONObject()
            .put("id", pin.id)
            .put("name", pin.name)
            .put("time", pin.time)
            .put("lat", pin.latitude)
            .put("lng", pin.longitude)
            .put("voices", pin.voices.joinToString(" · ") { it.speaker })
    }))
    put("selected", ui.selectedId ?: JSONObject.NULL)
}.toString()

/** 다녀온 길 길이(km). 경로 점 사이 거리를 더한다 */
private fun routeKm(route: List<Pair<Double, Double>>): Int =
    route.zipWithNext { (lat1, lon1), (lat2, lon2) ->
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        6371.0 * 2 * asin(sqrt(a))
    }.sum().roundToInt()

/** 인터넷이 없을 때 쓰는 평면 지도. 다녀온 길을 비스듬히 눕힌 판 위에 그린다 */
@Composable
private fun FlatMap(ui: AlbumMapUiState, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val tilt = remember { Animatable(0.15f) }
    LaunchedEffect(ui.loading) {
        if (!ui.loading) tilt.animateTo(1.35f, tween(1400, easing = FastOutSlowInEasing))
    }
    val measurer = rememberTextMeasurer()
    val projection = remember(ui.route, ui.pins) { MapProjection.of(ui.route, ui.pins) }
    Canvas(
        modifier
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(DoranPalette.SkyTop, DoranPalette.SkyMid, DoranPalette.SkyHorizon)))
            .pointerInput(ui.pins, projection) {
                detectTapGestures { tap ->
                    val hit = ui.pins.minByOrNull { pin ->
                        val head = projection.pinHead(pin, size.width.toFloat(), size.height.toFloat(), tilt.value)
                        hypot(head.x - tap.x, head.y - tap.y)
                    } ?: return@detectTapGestures
                    val head = projection.pinHead(hit, size.width.toFloat(), size.height.toFloat(), tilt.value)
                    if (hypot(head.x - tap.x, head.y - tap.y) < 90f) onSelect(hit.id)
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
