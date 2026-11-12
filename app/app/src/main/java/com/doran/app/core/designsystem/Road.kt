package com.doran.app.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

enum class RoadSide { LEFT, RIGHT, CENTER }

/**
 * 길가 표지판 하나. [depth]는 0(바로 앞)~1(지평선). 멀수록 작고 높이 서 있다.
 * [hot]이면 도란이 지금 제안하거나 경유지로 넣은 곳이라 주황으로 칠한다.
 * [ghost]면 아직 비어 있는 자리라 점선 테두리만 그린다(앨범 빈 상태).
 */
data class RoadSign(
    val id: String,
    val title: String,
    val sub: String,
    val depth: Float,
    val side: RoadSide,
    val hot: Boolean = false,
    val selected: Boolean = false,
    val ghost: Boolean = false,
    /** 길에서 더 떨어져 세울 거리. 같은 쪽 표지판끼리 겹치지 않게 앞뒤로 엇갈려 세울 때 쓴다 */
    val outset: Dp = 0.dp,
)

/** 거리(km)를 도로 깊이로. 가까운 곳은 넓게, 먼 곳은 지평선 쪽으로 몰린다 */
fun depthForKm(km: Double): Float = (km / (km + 40.0)).toFloat().coerceIn(0.06f, 0.92f)

/**
 * 원근 도로의 모양. 화면 크기(px)와 지평선 높이로 정한다.
 * 앞으로 간 거리 z(1이 화면 맨 아래)가 커질수록 지평선에 가까워진다. 깊이 d는 z = 1 + 5d.
 */
private class RoadGeometry(val width: Float, val height: Float, horizonFraction: Float) {
    val horizon = height * horizonFraction
    val bottom = height
    val vx = width / 2f
    val bottomHalf = width * 0.34f
    val topHalf = width * 0.018f

    private fun s(depth: Float): Float = 1f / (1f + depth * 5f)

    /** 그 깊이의 길바닥 높이 */
    fun groundY(depth: Float): Float = horizon + (bottom - horizon) * s(depth)

    /** 앞으로 z만큼 간 곳의 길바닥 높이 */
    fun yAt(z: Float): Float = horizon + (bottom - horizon) / z

    fun halfWidth(y: Float): Float = topHalf + (bottomHalf - topHalf) * ((y - horizon) / (bottom - horizon)).coerceIn(0f, 1f)

    /** 길 가운데에서 [f](1이면 길 가장자리)만큼 떨어진 곳의 x */
    fun xAt(z: Float, f: Float): Float = vx + f * halfWidth(yAt(z))

    /** 표지판 크기 비율. 너무 작아지지 않게 바닥을 둔다 */
    fun scale(depth: Float): Float = 0.55f + 0.45f * s(depth)

    fun poleX(sign: RoadSign, margin: Float): Float {
        val y = groundY(sign.depth)
        val half = halfWidth(y)
        return when (sign.side) {
            RoadSide.LEFT -> vx - half - margin * scale(sign.depth)
            RoadSide.RIGHT -> vx + half + margin * scale(sign.depth)
            RoadSide.CENTER -> vx
        }
    }

    /** 경로선이 그곳으로 휠 때 닿는 점. 길 가장자리 조금 안쪽 */
    fun viaPoint(sign: RoadSign): Offset {
        val y = groundY(sign.depth)
        val half = halfWidth(y) * 0.82f
        val x = when (sign.side) {
            RoadSide.LEFT -> vx - half
            RoadSide.RIGHT -> vx + half
            RoadSide.CENTER -> vx
        }
        return Offset(x, y)
    }
}

/** 표지판 판과 기둥. 처음 놓일 때 기둥이 땅에서 자라고 판이 떠오른다 */
@Composable
private fun SignPost(sign: RoadSign, pole: Dp, onClick: (() -> Unit)?) {
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // 가까운 것부터 차례로 선다
        rise.animateTo(1f, tween(560, delayMillis = (sign.depth * 360).roundToInt() + 120, easing = FastOutSlowInEasing))
    }
    val shape = RoundedCornerShape(12.dp)
    val fill = when {
        sign.ghost -> Color.Transparent
        sign.hot -> DoranPalette.Accent
        else -> DoranPalette.SurfaceHigh
    }
    val ink = if (sign.hot) DoranPalette.OnAccent else DoranPalette.Ink
    val inkSoft = if (sign.hot) DoranPalette.OnAccent.copy(alpha = 0.78f) else DoranPalette.InkSoft
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Column(
                Modifier
                    .alpha(rise.value)
                    .then(if (sign.ghost) Modifier else Modifier.shadow(10.dp, shape, ambientColor = Color.Black, spotColor = Color.Black))
                    .background(fill, shape)
                    .then(
                        when {
                            sign.ghost -> Modifier.dashedBorder(DoranPalette.InkMute, 12.dp)
                            sign.selected -> Modifier.border(4.dp, DoranPalette.Ink, shape)
                            else -> Modifier.border(1.dp, if (sign.hot) DoranPalette.Accent else DoranPalette.LineStrong, shape)
                        },
                    )
                    .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                    .padding(horizontal = 22.dp, vertical = 14.dp),
            ) {
                Text(sign.title, style = DoranText.sign, color = if (sign.ghost) DoranPalette.InkSoft else ink, maxLines = 1)
                if (sign.sub.isNotBlank()) {
                    Text(sign.sub, style = DoranText.signSub, color = if (sign.ghost) DoranPalette.InkMute else inkSoft, maxLines = 1)
                }
            }
        }
        // 기둥. 그늘진 쪽을 한 줄 더 그려 둥근 쇠기둥처럼 보이게 한다
        Canvas(Modifier.width(8.dp).height(pole * rise.value)) {
            val c = if (sign.hot) DoranPalette.Accent else DoranPalette.RailShade
            val w = 5.dp.toPx()
            drawRect(if (sign.ghost) DoranPalette.InkMute.copy(alpha = 0.6f) else c, topLeft = Offset((size.width - w) / 2f, 0f), size = Size(w, size.height))
            if (!sign.ghost) drawRect(Color.White.copy(alpha = 0.18f), topLeft = Offset((size.width - w) / 2f, 0f), size = Size(w * 0.35f, size.height))
        }
    }
}

private fun Modifier.dashedBorder(color: Color, radius: Dp): Modifier = drawWithCache {
    val stroke = Stroke(width = 3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx())))
    val r = radius.toPx()
    onDrawBehind {
        drawRoundRect(color, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r), style = stroke)
    }
}

/**
 * 차 앞 도로. 시간대가 묻어나는 하늘과 겹겹이 멀어지는 산, 아스팔트 결이 있는 길과 차선, 갓길과 가드레일,
 * 지평선 끝 목적지까지 이어지는 주황 경로선. 길가에 [signs]가 실제 거리만큼 떨어져 서 있다.
 * [orb]는 내 차 자리(아래 가운데)에 놓이고, 도란의 빛이 그 아래 길바닥에 비친다.
 * [via]에 표지판 id를 주면 경로선이 그곳을 거쳐 가도록 휜다(경유지). [horizon]은 지평선 높이(화면 비율).
 * [flow]면 차선이 아주 천천히 다가온다. 정차 화면에서만 켠다(주행 중에는 움직이는 것을 두지 않는다).
 */
@Composable
fun RoadScene(
    modifier: Modifier = Modifier,
    signs: List<RoadSign> = emptyList(),
    horizon: Float = 0.40f,
    orbSize: Dp = 160.dp,
    orbLift: Dp = 28.dp,
    showRoute: Boolean = true,
    via: String? = null,
    flow: Boolean = false,
    orbLight: Boolean = true,
    onSignClick: ((String) -> Unit)? = null,
    orb: @Composable () -> Unit = {},
) {
    val density = LocalDensity.current
    // 경유지가 생기면 곧은 선에서 휜 선으로 넘어간다
    val viaSign = signs.firstOrNull { it.id == via }
    val bend by animateFloatAsState(if (viaSign != null) 1f else 0f, tween(900, easing = FastOutSlowInEasing), label = "bend")
    val phase = lanePhase(flow)
    val pole = 64.dp

    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val g = RoadGeometry(w, h, horizon)
        val orbPx = with(density) { orbSize.toPx() }
        val liftPx = with(density) { orbLift.toPx() }
        val margin = with(density) { 52.dp.toPx() }
        val orbTop = h - liftPx - orbPx

        // 하늘, 산, 땅, 길바닥, 가드레일. 크기와 낮·밤이 바뀔 때만 다시 만든다.
        // 도란이 매 프레임 움직여도 이 큰 그림은 다시 그리지 않도록 따로 떼어 한 장으로 둔다(소프트웨어 렌더링 에뮬레이터에서 특히 무겁다)
        Spacer(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithCache { buildLandscape(g) },
        )

        Canvas(Modifier.fillMaxSize()) {
            drawLanes(g, phase.value)
            if (orbLight) drawOrbLight(g, orbPx, liftPx)

            if (showRoute) {
                val from = Offset(g.vx, orbTop + orbPx * 0.1f)
                val end = Offset(g.vx, g.horizon)
                val target = viaSign
                if (target == null || bend <= 0.001f) {
                    // 곧은 경로선. 멀수록 가늘게
                    val bw = 4.dp.toPx()
                    val tw = 1.dp.toPx()
                    val route = Path().apply {
                        moveTo(from.x - bw, from.y)
                        lineTo(from.x + bw, from.y)
                        lineTo(end.x + tw, end.y)
                        lineTo(end.x - tw, end.y)
                        close()
                    }
                    drawPath(route, DoranPalette.Accent.copy(alpha = 0.9f))
                } else {
                    // 경유지를 거쳐 가는 선. 경유지 표지판 깊이의 길바닥, 길 가장자리 안쪽을 거쳐 간다
                    val v = g.viaPoint(target)
                    val via = Offset(g.vx + (v.x - g.vx) * bend, v.y)
                    // 위쪽 곡선 제어점은 그 높이의 길 안쪽으로 묶어 선이 길 밖으로 나가지 않게 한다
                    val upY = via.y - (via.y - end.y) * 0.45f
                    val upHalf = g.halfWidth(upY) * 0.82f
                    val upX = via.x.coerceIn(g.vx - upHalf, g.vx + upHalf)
                    val path = Path().apply {
                        moveTo(from.x, from.y)
                        cubicTo(from.x, from.y - (from.y - via.y) * 0.55f, via.x, via.y + (from.y - via.y) * 0.45f, via.x, via.y)
                        cubicTo(upX, upY, end.x, end.y + (via.y - end.y) * 0.35f, end.x, end.y)
                    }
                    drawPath(path, DoranPalette.Accent.copy(alpha = 0.9f), style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    // 휜 점에서 표지판 기둥 밑까지 짧은 점선
                    val poleBase = Offset(g.poleX(target, margin), g.groundY(target.depth))
                    drawLine(
                        DoranPalette.Accent.copy(alpha = bend),
                        via,
                        poleBase,
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())),
                    )
                    drawCircle(DoranPalette.Accent, radius = 9.dp.toPx() * bend, center = via)
                    drawCircle(DoranPalette.RoadNear, radius = 4.dp.toPx() * bend, center = via)
                }
            }
        }

        // 표지판. 먼 것부터 놓아 가까운 것이 위에 온다. 판과 기둥을 함께 줄여 기둥 끝이 길바닥에 닿게 한다
        val ordered = signs.sortedByDescending { it.depth }
        Layout(
            content = {
                ordered.forEach { sign ->
                    key(sign.id) { SignPost(sign, pole, onSignClick?.takeIf { !sign.ghost }?.let { click -> { click(sign.id) } }) }
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, c ->
            val placeables = measurables.map { it.measure(c.copy(minWidth = 0, minHeight = 0)) }
            layout(c.maxWidth, c.maxHeight) {
                placeables.forEachIndexed { i, p ->
                    val sign = ordered[i]
                    val k = g.scale(sign.depth)
                    val x = g.poleX(sign, margin + sign.outset.toPx())
                    // 기둥이 자라는 동안에도 판이 기둥 끝에 붙어 있게, 다 자랐을 때의 바닥 기준으로 놓는다
                    val bottom = g.groundY(sign.depth)
                    p.placeWithLayer((x - p.width / 2f).roundToInt(), (bottom - p.height).roundToInt()) {
                        scaleX = k
                        scaleY = k
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                }
            }
        }

        Box(
            Modifier
                .padding(start = with(density) { ((w - orbPx) / 2f).toDp() }, top = with(density) { orbTop.toDp() })
                .size(orbSize),
        ) { orb() }
    }
}

// 차선 한 칸(칠한 곳과 빈 곳)의 길이와 칠한 길이. z 단위
private const val DASH_PERIOD = 0.9f
private const val DASH_LENGTH = 0.38f

/** 차선이 다가오는 정도. [flow]가 꺼져 있으면 0에 머문다. 한 칸 지나는 데 8초 */
@Composable
private fun lanePhase(flow: Boolean): State<Float> {
    if (!flow) return remember { mutableFloatStateOf(0f) }
    return rememberInfiniteTransition(label = "lanes").animateFloat(
        initialValue = 0f,
        targetValue = DASH_PERIOD,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
}

/** 겹산 능선. 사인 몇 개를 겹쳐 자연스럽게 오르내리게 한다. [valley]만큼 길이 지나는 가운데가 낮아진다 */
private fun ridgePath(g: RoadGeometry, height: Float, seed: Float, valley: Float): Path = Path().apply {
    moveTo(0f, g.horizon + 1f)
    val step = g.width / 160f
    var x = 0f
    while (x <= g.width + step) {
        val u = x / g.width
        val n = 0.55f + 0.22f * sin(u * 7.1f + seed) + 0.14f * sin(u * 15.7f + seed * 2.3f) + 0.07f * sin(u * 37f + seed * 0.7f)
        val dip = 1f - valley * exp(-((u - 0.5f) / 0.11f).let { it * it })
        lineTo(x, g.horizon - height * n * dip)
        x += step
    }
    lineTo(g.width, g.horizon + 1f)
    close()
}

private fun androidx.compose.ui.draw.CacheDrawScope.buildLandscape(g: RoadGeometry): androidx.compose.ui.draw.DrawResult {
    val day = DoranPalette.isDay
    val wet = DoranPalette.isWet
    val h = g.height
    val w = g.width
    // 흐린 날은 하늘이 회색으로 가라앉는다
    val overcast = if (wet) (if (day) Color(0xFFA9B0B6) else Color(0xFF151A21)) else null
    fun sky(c: Color) = overcast?.let { lerp(c, it, 0.7f) } ?: c
    val skyBrush = Brush.verticalGradient(
        0f to sky(DoranPalette.SkyTop),
        0.55f to sky(DoranPalette.SkyMid),
        1f to sky(DoranPalette.SkyHorizon),
        startY = 0f,
        endY = g.horizon,
    )
    val haze = sky(DoranPalette.SkyHorizon)
    val ridges = listOf(
        ridgePath(g, h * 0.10f, 1.3f, 0.15f) to sky(DoranPalette.RidgeFar),
        ridgePath(g, h * 0.07f, 4.1f, 0.35f) to sky(DoranPalette.RidgeMid),
        ridgePath(g, h * 0.042f, 2.2f, 0.7f) to DoranPalette.RidgeNear,
    )
    val groundBrush = Brush.verticalGradient(0f to DoranPalette.GroundFar, 1f to DoranPalette.GroundNear, startY = g.horizon, endY = h)

    // 포장면(갓길 포함)과 그 바깥 자갈 띠
    fun band(f0: Float, f1: Float): Path = Path().apply {
        moveTo(g.vx + f0 * g.bottomHalf, g.bottom)
        lineTo(g.vx + f1 * g.bottomHalf, g.bottom)
        lineTo(g.vx + f1 * g.topHalf, g.horizon)
        lineTo(g.vx + f0 * g.topHalf, g.horizon)
        close()
    }
    val road = band(-1f, 1f)
    val gravelL = band(-1.07f, -1f)
    val gravelR = band(1f, 1.07f)
    val darken = if (wet) 0.28f else 0f
    fun wetRoad(c: Color) = lerp(c, Color.Black, darken)
    val roadBrush = Brush.verticalGradient(0f to wetRoad(DoranPalette.RoadFar), 0.35f to wetRoad(lerp(DoranPalette.RoadFar, DoranPalette.RoadNear, 0.75f)), 1f to wetRoad(DoranPalette.RoadNear), startY = g.horizon, endY = h)

    // 가장자리 실선. 멀수록 가늘어지는 띠
    fun solidLine(f: Float, widthBottom: Float): Path = Path().apply {
        moveTo(g.vx + f * g.bottomHalf - widthBottom / 2f * kotlin.math.sign(f), g.bottom)
        lineTo(g.vx + f * g.bottomHalf + widthBottom / 2f * kotlin.math.sign(f), g.bottom)
        lineTo(g.vx + f * g.topHalf, g.horizon)
        close()
    }
    val edgeW = g.bottomHalf * 0.022f
    val edges = listOf(solidLine(-0.94f, edgeW), solidLine(0.94f, edgeW))

    // 아스팔트 결. 위치는 고정된 씨앗으로 정해 매번 같은 결이 나온다
    val rnd = java.util.Random(11)
    val grainLight = ArrayList<Offset>()
    val grainDark = ArrayList<Offset>()
    val grainNear = ArrayList<Offset>()
    repeat(2600) {
        val z = 1f + 9f * rnd.nextFloat().let { it * it }
        val f = rnd.nextFloat() * 2f - 1f
        val p = Offset(g.xAt(z, f), g.yAt(z))
        when {
            z < 1.6f -> grainNear.add(p)
            rnd.nextBoolean() -> grainLight.add(p)
            else -> grainDark.add(p)
        }
    }
    val grainSize = g.width / 1400f

    // 가드레일. 길 가장자리 바깥에 빔과 기둥, 기둥마다 반사판
    val railF = 1.13f
    val railH = (h - g.horizon) * 0.07f
    fun rail(side: Float): Path = Path().apply {
        val zs = generateSequence(1f) { it * 1.06f }.takeWhile { it < 60f }.toList()
        zs.forEachIndexed { i, z ->
            val x = g.xAt(z, side * railF)
            val y = g.yAt(z) - railH * 0.55f / z
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        zs.reversed().forEach { z -> lineTo(g.xAt(z, side * railF), g.yAt(z) - railH / z) }
        close()
    }
    val rails = listOf(rail(-1f), rail(1f))
    data class Post(val top: Offset, val bottom: Offset, val width: Float, val z: Float)
    val posts = buildList {
        var z = 1.05f
        while (z < 26f) {
            for (side in listOf(-1f, 1f)) {
                val x = g.xAt(z, side * railF)
                add(Post(Offset(x, g.yAt(z) - railH / z), Offset(x, g.yAt(z)), g.bottomHalf * 0.012f / z, z))
            }
            z += 0.75f
        }
    }

    val wetSheen = if (wet) {
        Brush.verticalGradient(
            0f to haze.copy(alpha = if (day) 0.75f else 0.5f),
            0.6f to haze.copy(alpha = if (day) 0.22f else 0.14f),
            1f to Color.Transparent,
            startY = g.horizon,
            endY = h,
        )
    } else {
        null
    }

    return onDrawBehind {
        drawRect(skyBrush, size = Size(w, g.horizon + 2f))
        // 지평선 위로 번지는 빛. 낮에는 엷은 볕, 밤에는 먼 도시의 기운
        softGlow(haze.copy(alpha = if (day) 0.9f else 0.7f), Offset(g.vx, g.horizon), w * 0.5f, h * 0.22f)
        drawRect(groundBrush, topLeft = Offset(0f, g.horizon), size = Size(w, h - g.horizon))
        ridges.forEach { (path, color) -> drawPath(path, color) }
        drawPath(gravelL, DoranPalette.Shoulder)
        drawPath(gravelR, DoranPalette.Shoulder)
        drawPath(road, roadBrush)
        drawPoints(grainDark, PointMode.Points, Color.Black.copy(alpha = if (day) 0.16f else 0.32f), strokeWidth = grainSize * 1.6f, cap = StrokeCap.Square)
        drawPoints(grainLight, PointMode.Points, Color.White.copy(alpha = if (day) 0.10f else 0.035f), strokeWidth = grainSize * 1.6f, cap = StrokeCap.Square)
        drawPoints(grainNear, PointMode.Points, Color.White.copy(alpha = if (day) 0.09f else 0.04f), strokeWidth = grainSize * 3f, cap = StrokeCap.Square)
        wetSheen?.let { drawPath(road, it) }
        edges.forEach { drawPath(it, DoranPalette.EdgeLine.copy(alpha = if (day) 0.95f else 0.55f)) }
        // 가드레일 기둥 → 빔 → 반사판 순서로 쌓는다
        posts.forEach { p -> drawLine(DoranPalette.RailShade, p.top, p.bottom, strokeWidth = p.width.coerceAtLeast(1f)) }
        rails.forEach { drawPath(it, DoranPalette.Rail) }
        posts.forEach { p ->
            val glow = (1.6f / p.z).coerceIn(0.15f, 1f)
            drawCircle(DoranPalette.Reflector.copy(alpha = if (day) 0.55f * glow else 0.9f * glow), radius = (p.width * 0.9f).coerceAtLeast(1f), center = Offset(p.top.x, p.top.y + railH * 0.22f / p.z))
        }
        // 지평선 쪽 공기. 멀수록 하늘색에 묻힌다
        val mistTop = g.horizon - h * 0.035f
        val mistBottom = g.horizon + (h - g.horizon) * 0.26f
        val mist = if (day) 0.5f else 0.8f
        drawRect(
            Brush.verticalGradient(
                0f to haze.copy(alpha = 0f),
                (g.horizon - mistTop) / (mistBottom - mistTop) to haze.copy(alpha = mist),
                1f to haze.copy(alpha = 0f),
                startY = mistTop,
                endY = mistBottom,
            ),
            topLeft = Offset(0f, mistTop),
            size = Size(w, mistBottom - mistTop),
        )
        // 밤에는 전조등이 비추는 앞쪽 길만 조금 밝다
        if (!day) {
            softGlow(Color(0xFFFFF3DD).copy(alpha = 0.09f), Offset(g.vx, h * 1.02f), g.bottomHalf * 1.15f, (h - g.horizon) * 0.62f)
        }
    }
}

/** 차선 두 줄(세 차로). 깊이를 따라 길이와 굵기가 줄고, 밤에는 전조등이 닿는 가까운 쪽만 빛난다 */
private fun DrawScope.drawLanes(g: RoadGeometry, phase: Float) {
    val day = DoranPalette.isDay
    val base = DoranPalette.Lane
    val far = DoranPalette.RoadFar
    val laneW = g.bottomHalf * 0.013f
    for (f in listOf(-1f / 3f, 1f / 3f)) {
        var start = 1f - DASH_PERIOD + phase
        while (start < 40f) {
            val z1 = start.coerceAtLeast(0.9f)
            val z2 = start + DASH_LENGTH
            start += DASH_PERIOD
            if (z2 <= 0.9f) continue
            val y1 = g.yAt(z1)
            val y2 = g.yAt(z2)
            if (y1 - y2 < 0.8f) break
            val w1 = laneW / z1
            val w2 = laneW / z2
            val x1 = g.xAt(z1, f)
            val x2 = g.xAt(z2, f)
            val near = (1.4f / z1).coerceIn(0f, 1f)
            val color = if (day) lerp(far, base, (0.35f + 0.65f * near)) else lerp(far, base, 0.25f + 0.75f * near * near)
            val dash = Path().apply {
                moveTo(x1 - w1, y1)
                lineTo(x1 + w1, y1)
                lineTo(x2 + w2, y2)
                lineTo(x2 - w2, y2)
                close()
            }
            drawPath(dash, color)
        }
    }
}

/** 가장자리가 보이지 않는 타원 빛. 원형 그라디언트를 세로로 눌러 그린다 */
private fun DrawScope.softGlow(color: Color, center: Offset, rx: Float, ry: Float) {
    scale(1f, ry / rx, pivot = center) {
        drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = color.alpha * 0.45f), Color.Transparent), center = center, radius = rx), radius = rx, center = center)
    }
}

/** 도란 빛이 길바닥에 비친다. 젖은 길이면 세로로 길게 끌린다 */
private fun DrawScope.drawOrbLight(g: RoadGeometry, orbPx: Float, liftPx: Float) {
    val day = DoranPalette.isDay
    val wet = DoranPalette.isWet
    val cy = g.height - liftPx
    val glow = DoranPalette.Accent.copy(alpha = if (day) 0.26f else 0.36f)
    softGlow(glow, Offset(g.vx, cy), orbPx * 1.3f, orbPx * 0.34f)
    // 젖은 길에는 도란 빛이 아래로 길게 끌린다
    if (wet) softGlow(glow, Offset(g.vx, cy + orbPx * 0.2f), orbPx * 0.42f, orbPx * 1.1f)
    // 길에 닿은 그림자
    drawOval(
        Color.Black.copy(alpha = if (day) 0.30f else 0.45f),
        topLeft = Offset(g.vx - orbPx * 0.40f, cy - orbPx * 0.07f),
        size = Size(orbPx * 0.80f, orbPx * 0.14f),
    )
}

private val HHMM = java.time.format.DateTimeFormatter.ofPattern("HH:mm")

/** 앨범과 같은 서울 시각 */
fun clockText(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.of("Asia/Seoul")).format(HHMM)
