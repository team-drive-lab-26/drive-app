package com.doran.app.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

enum class RoadSide { LEFT, RIGHT, CENTER }

/**
 * 길가 표지판 하나. [depth]는 0(바로 앞)~1(지평선). 멀수록 작고 높이 서 있다.
 * [hot]이면 도란이 지금 제안하거나 경유지로 넣은 곳이라 주황으로 칠한다.
 */
data class RoadSign(
    val id: String,
    val title: String,
    val sub: String,
    val depth: Float,
    val side: RoadSide,
    val hot: Boolean = false,
)

/** 거리(km)를 도로 깊이로. 가까운 곳은 넓게, 먼 곳은 지평선 쪽으로 몰린다 */
fun depthForKm(km: Double): Float = (km / (km + 80.0)).toFloat().coerceIn(0.04f, 0.92f)

/** 원근 도로의 모양. 화면 크기(px)와 지평선 높이로 정한다 */
private class RoadGeometry(val width: Float, val height: Float, horizonFraction: Float) {
    val horizon = height * horizonFraction
    val bottom = height
    val vx = width / 2f
    val bottomHalf = width * 0.34f
    val topHalf = width * 0.018f

    private fun s(depth: Float): Float = 1f / (1f + depth * 5f)

    /** 그 깊이의 길바닥 높이 */
    fun groundY(depth: Float): Float = horizon + (bottom - horizon) * s(depth)

    fun halfWidth(y: Float): Float = topHalf + (bottomHalf - topHalf) * ((y - horizon) / (bottom - horizon)).coerceIn(0f, 1f)

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
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier
                .alpha(rise.value)
                .background(if (sign.hot) DoranPalette.Accent else DoranPalette.SurfaceHigh, shape)
                .border(1.dp, if (sign.hot) DoranPalette.Accent else DoranPalette.LineStrong, shape)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(sign.title, style = DoranText.sign, color = if (sign.hot) DoranPalette.OnAccent else DoranPalette.Ink, maxLines = 1)
            if (sign.sub.isNotBlank()) {
                Text(sign.sub, style = DoranText.signSub, color = if (sign.hot) DoranPalette.OnAccent else DoranPalette.InkSoft, maxLines = 1)
            }
        }
        Box(
            Modifier
                .width(4.dp)
                .height(pole * rise.value)
                .background(if (sign.hot) DoranPalette.Accent else DoranPalette.InkMute),
        )
    }
}

/**
 * 차 앞 도로. 지평선과 먼 산 능선, 원근으로 좁아지는 길, 지평선 끝 목적지까지 이어지는 주황 경로선.
 * 길가에 [signs]가 실제 거리만큼 떨어져 서 있다. [orb]는 내 차 자리(아래 가운데)에 놓인다.
 * [via]에 표지판 id를 주면 경로선이 그곳을 거쳐 가도록 휜다(경유지). [horizon]은 지평선 높이(화면 비율).
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
    onSignClick: ((String) -> Unit)? = null,
    orb: @Composable () -> Unit = {},
) {
    val density = LocalDensity.current
    // 경유지가 생기면 곧은 선에서 휜 선으로 넘어간다
    val viaSign = signs.firstOrNull { it.id == via }
    val bend by animateFloatAsState(if (viaSign != null) 1f else 0f, tween(900, easing = FastOutSlowInEasing), label = "bend")
    val pole = 64.dp

    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val g = RoadGeometry(w, h, horizon)
        val orbPx = with(density) { orbSize.toPx() }
        val liftPx = with(density) { orbLift.toPx() }
        val margin = with(density) { 36.dp.toPx() }
        val orbTop = h - liftPx - orbPx

        Canvas(Modifier.fillMaxSize()) {
            // 먼 산 능선
            val ridge = Path().apply {
                val pts = listOf(0f to 0.0f, 0.09f to 0.55f, 0.18f to 0.25f, 0.27f to 0.85f, 0.37f to 0.4f, 0.44f to 0.95f, 0.51f to 0.5f, 0.6f to 0.8f, 0.69f to 0.3f, 0.79f to 0.88f, 0.88f to 0.45f, 1f to 0.7f)
                moveTo(0f, g.horizon)
                pts.forEach { (x, k) -> lineTo(x * w, g.horizon - k * h * 0.075f) }
                lineTo(w, g.horizon)
                close()
            }
            drawPath(ridge, DoranPalette.Ridge)
            drawLine(DoranPalette.Line, Offset(0f, g.horizon), Offset(w, g.horizon), strokeWidth = 1.dp.toPx())

            // 길
            val road = Path().apply {
                moveTo(g.vx - g.bottomHalf, g.bottom)
                lineTo(g.vx + g.bottomHalf, g.bottom)
                lineTo(g.vx + g.topHalf, g.horizon)
                lineTo(g.vx - g.topHalf, g.horizon)
                close()
            }
            drawPath(road, DoranPalette.Road)
            val edge = 3.dp.toPx()
            drawLine(DoranPalette.RoadEdge, Offset(g.vx - g.bottomHalf, g.bottom), Offset(g.vx - g.topHalf, g.horizon), strokeWidth = edge)
            drawLine(DoranPalette.RoadEdge, Offset(g.vx + g.bottomHalf, g.bottom), Offset(g.vx + g.topHalf, g.horizon), strokeWidth = edge)

            // 차선. 깊이마다 짧아지고 가늘어진다
            listOf(0.02f, 0.12f, 0.26f, 0.45f, 0.7f).forEach { d ->
                val y1 = g.groundY(d)
                val y2 = g.groundY(d + 0.05f)
                val k = g.scale(d)
                for (side in listOf(-1f, 1f)) {
                    drawLine(
                        DoranPalette.LaneMark,
                        Offset(g.vx + side * g.halfWidth(y1) * 0.5f, y1),
                        Offset(g.vx + side * g.halfWidth(y2) * 0.5f, y2),
                        strokeWidth = 5.dp.toPx() * k,
                    )
                }
            }

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
                    // 경유지를 거쳐 가는 선. 곧은 선 위의 같은 높이 점에서 경유지 쪽으로 벌어진다
                    val v = g.viaPoint(target)
                    val straight = Offset(g.vx, v.y)
                    val via = Offset(straight.x + (v.x - straight.x) * bend, v.y)
                    val path = Path().apply {
                        moveTo(from.x, from.y)
                        quadraticTo(via.x, (from.y + via.y) / 2f, via.x, via.y)
                        quadraticTo(via.x, (via.y + end.y) / 2f + (end.y - via.y) * 0.2f, end.x, end.y)
                    }
                    drawPath(path, DoranPalette.Accent.copy(alpha = 0.9f), style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawCircle(DoranPalette.Accent, radius = 9.dp.toPx() * bend, center = via)
                    drawCircle(DoranPalette.Background, radius = 4.dp.toPx() * bend, center = via)
                }
            }
        }

        // 표지판. 먼 것부터 놓아 가까운 것이 위에 온다. 판과 기둥을 함께 줄여 기둥 끝이 길바닥에 닿게 한다
        val ordered = signs.sortedByDescending { it.depth }
        Layout(
            content = {
                ordered.forEach { sign ->
                    key(sign.id) { SignPost(sign, pole, onSignClick?.let { click -> { click(sign.id) } }) }
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, c ->
            val placeables = measurables.map { it.measure(c.copy(minWidth = 0, minHeight = 0)) }
            layout(c.maxWidth, c.maxHeight) {
                placeables.forEachIndexed { i, p ->
                    val sign = ordered[i]
                    val k = g.scale(sign.depth)
                    val x = g.poleX(sign, margin)
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

private val HHMM = java.time.format.DateTimeFormatter.ofPattern("HH:mm")

/** 앨범과 같은 서울 시각 */
fun clockText(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.of("Asia/Seoul")).format(HHMM)
