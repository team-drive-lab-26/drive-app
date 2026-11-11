package com.doran.app.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

enum class RoadSide { LEFT, RIGHT, CENTER }

/**
 * 길가 표지판 하나. [depth]는 0(바로 앞)~1(지평선). 멀수록 작고 높이 서 있다.
 * [hot]이면 도란이 지금 제안하는 곳이라 주황으로 칠한다.
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
fun depthForKm(km: Double): Float = (km / (km + 40.0)).toFloat().coerceIn(0.04f, 0.95f)

/** 원근 도로의 모양. 화면 크기(px)와 지평선 높이로 정한다 */
private class RoadGeometry(val width: Float, val height: Float, horizonFraction: Float) {
    val horizon = height * horizonFraction
    val bottom = height
    val vx = width / 2f
    val bottomHalf = width * 0.26f
    val topHalf = width * 0.018f

    private fun s(depth: Float): Float = 1f / (1f + depth * 9f)

    /** 그 깊이의 길바닥 높이 */
    fun groundY(depth: Float): Float = horizon + (bottom - horizon) * s(depth)

    fun halfWidth(y: Float): Float = topHalf + (bottomHalf - topHalf) * ((y - horizon) / (bottom - horizon)).coerceIn(0f, 1f)

    /** 표지판 크기 비율. 너무 작아지지 않게 바닥을 둔다 */
    fun scale(depth: Float): Float = 0.42f + 0.58f * s(depth)

    fun poleX(sign: RoadSign, margin: Float): Float {
        val y = groundY(sign.depth)
        val half = halfWidth(y)
        return when (sign.side) {
            RoadSide.LEFT -> vx - half - margin * scale(sign.depth)
            RoadSide.RIGHT -> vx + half + margin * scale(sign.depth)
            RoadSide.CENTER -> vx
        }
    }
}

@Composable
private fun SignPlate(sign: RoadSign) {
    val shape = RoundedCornerShape(12.dp)
    val bg = if (sign.hot) DoranPalette.Accent else DoranPalette.SurfaceHigh
    Column(
        Modifier
            .background(bg, shape)
            .border(1.dp, if (sign.hot) DoranPalette.Accent else DoranPalette.LineStrong, shape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(sign.title, style = DoranText.sign, color = if (sign.hot) DoranPalette.OnAccent else DoranPalette.Ink, maxLines = 1)
        if (sign.sub.isNotBlank()) {
            Text(sign.sub, style = DoranText.signSub, color = if (sign.hot) DoranPalette.OnAccent else DoranPalette.InkSoft, maxLines = 1)
        }
    }
}

/**
 * 차 앞 도로. 지평선과 먼 산 능선, 원근으로 좁아지는 길, 지평선 끝 목적지까지 이어지는 귤색 경로선.
 * 길가에 [signs]가 실제 거리만큼 떨어져 서 있다. [orb]는 내 차 자리(아래 가운데)에 놓인다.
 * [horizon]은 지평선 높이(화면 비율). 주행 화면은 [signs]를 비워 길과 경로선만 남긴다.
 */
@Composable
fun RoadScene(
    modifier: Modifier = Modifier,
    signs: List<RoadSign> = emptyList(),
    horizon: Float = 0.40f,
    orbSize: Dp = 150.dp,
    orbLift: Dp = 28.dp,
    showRoute: Boolean = true,
    orb: @Composable () -> Unit = {},
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val g = RoadGeometry(w, h, horizon)
        val orbPx = with(density) { orbSize.toPx() }
        val liftPx = with(density) { orbLift.toPx() }
        val margin = with(density) { 36.dp.toPx() }
        val pole = with(density) { 64.dp.toPx() }
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
                    val x1 = g.vx + side * g.halfWidth(y1) * 0.5f
                    val x2 = g.vx + side * g.halfWidth(y2) * 0.5f
                    drawLine(DoranPalette.LaneMark, Offset(x1, y1), Offset(x2, y2), strokeWidth = 5.dp.toPx() * k)
                }
            }

            // 경로선. 도란 위에서 지평선 끝까지, 멀수록 가늘게
            if (showRoute) {
                val from = orbTop + orbPx * 0.1f
                val bw = 4.dp.toPx()
                val tw = 1.dp.toPx()
                val route = Path().apply {
                    moveTo(g.vx - bw, from)
                    lineTo(g.vx + bw, from)
                    lineTo(g.vx + tw, g.horizon)
                    lineTo(g.vx - tw, g.horizon)
                    close()
                }
                drawPath(route, DoranPalette.Accent.copy(alpha = 0.9f))
            }

            // 표지판 기둥
            signs.forEach { sign ->
                val x = g.poleX(sign, margin)
                val y = g.groundY(sign.depth)
                val k = g.scale(sign.depth)
                drawLine(
                    if (sign.hot) DoranPalette.Accent else DoranPalette.InkMute,
                    Offset(x, y),
                    Offset(x, y - pole * k),
                    strokeWidth = 3.dp.toPx() * k,
                )
            }
        }

        // 표지판 판. 먼 것부터 놓아 가까운 것이 위에 온다
        val ordered = signs.sortedByDescending { it.depth }
        Layout(content = { ordered.forEach { SignPlate(it) } }, modifier = Modifier.fillMaxSize()) { measurables, c ->
            val placeables = measurables.map { it.measure(c.copy(minWidth = 0, minHeight = 0)) }
            layout(c.maxWidth, c.maxHeight) {
                placeables.forEachIndexed { i, p ->
                    val sign = ordered[i]
                    val k = g.scale(sign.depth)
                    val x = g.poleX(sign, margin)
                    val bottomY = g.groundY(sign.depth) - pole * k
                    // 판은 기둥 끝에 아래 가운데를 맞추고 거리만큼 줄인다
                    p.placeWithLayer((x - p.width / 2f).roundToInt(), (bottomY - p.height).roundToInt()) {
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
