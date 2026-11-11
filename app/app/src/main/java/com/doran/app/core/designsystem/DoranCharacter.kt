package com.doran.app.core.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** 도란이 지금 하고 있는 일. 마이크 아이콘 대신 도란의 몸과 눈으로 보여 준다 */
enum class DoranMood { IDLE, LISTENING, THINKING, SPEAKING, HAPPY }

/**
 * 도란 캐릭터. 무광 주황 공에 눈 두 개.
 * 듣는 중에는 운전석 쪽으로 기울고 눈이 커지며, 생각할 때는 눈이 위로 돌고, 말할 때는 목소리 크기만큼 몸이 눌렸다 늘어난다.
 * [level]은 0~1 사이의 마이크·목소리 크기, [cheerKey]가 바뀌면 한 번 웃으며 뛴다(경유지 추가, 녹음 저장).
 */
@Composable
fun DoranCharacter(
    mood: DoranMood,
    modifier: Modifier = Modifier,
    level: Float = 0f,
    cheerKey: Long = 0L,
) {
    val time by produceState(0f) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { value = (it - start) / 1_000_000_000f }
    }
    var cheering by remember { mutableStateOf(false) }
    LaunchedEffect(cheerKey) {
        if (cheerKey == 0L) return@LaunchedEffect
        cheering = true
        delay(CHEER_MS)
        cheering = false
    }
    val shown = if (cheering) DoranMood.HAPPY else mood

    val soft = spring<Float>(stiffness = Spring.StiffnessLow)
    val lean by animateFloatAsState(if (shown == DoranMood.LISTENING) 1f else 0f, soft, label = "lean")
    val eyeScale by animateFloatAsState(if (shown == DoranMood.LISTENING) 1.18f else 1f, soft, label = "eye")
    val voice by animateFloatAsState(level.coerceIn(0f, 1f), tween(90), label = "level")
    val gazeX by animateFloatAsState(gazeX(shown, time), soft, label = "gazeX")
    val gazeY by animateFloatAsState(gazeY(shown, time), soft, label = "gazeY")

    Canvas(modifier) {
        drawDoran(shown, time, lean, eyeScale, voice, gazeX, gazeY)
    }
}

private fun gazeX(mood: DoranMood, t: Float) = when (mood) {
    DoranMood.IDLE -> 0.35f * sin(t * 0.37f) + 0.1f * sin(t * 1.3f)
    DoranMood.LISTENING -> -0.55f
    DoranMood.THINKING -> 0.5f * cos(t * 1.7f)
    DoranMood.SPEAKING -> -0.3f
    DoranMood.HAPPY -> 0f
}

private fun gazeY(mood: DoranMood, t: Float) = when (mood) {
    DoranMood.IDLE -> 0.12f * sin(t * 0.53f)
    DoranMood.LISTENING -> 0.05f
    DoranMood.THINKING -> -0.6f + 0.15f * sin(t * 1.7f)
    DoranMood.SPEAKING -> 0.1f
    DoranMood.HAPPY -> 0f
}

private fun DrawScope.drawDoran(
    mood: DoranMood,
    t: Float,
    lean: Float,
    eyeScale: Float,
    level: Float,
    gazeX: Float,
    gazeY: Float,
) {
    val r = size.minDimension * 0.34f
    val ground = size.height * 0.5f + r

    // 말할 때 목소리가 안 들어오는 엔진(안드로이드 기본 음성)이면 말하는 리듬을 흉내 낸다
    val speak = when (mood) {
        DoranMood.SPEAKING -> maxOf(level, 0.25f + 0.45f * abs(sin(t * 8.7f)) * abs(sin(t * 2.9f)))
        DoranMood.LISTENING -> level * 0.6f
        else -> 0f
    }
    val breath = 0.012f * sin(t * 1.9f)
    val squash = breath + 0.07f * speak
    val scaleX = 1f + squash * 0.8f
    val scaleY = 1f - squash
    val hop = if (mood == DoranMood.HAPPY) abs(sin(t * 7f)) * r * 0.16f else 0f
    val tilt = when (mood) {
        DoranMood.THINKING -> 6f * sin(t * 1.3f)
        else -> 0f
    } - lean * 8f

    val w = r * 2f * scaleX
    val h = r * 1.94f * scaleY
    val cx = size.width / 2f - lean * r * 0.12f
    val top = ground - h - hop
    val cy = top + h / 2f

    // 바닥 그림자. 뛰면 옅어진다
    val shadowW = r * 1.7f * (1f - hop / (r * 0.6f))
    drawOval(
        color = Color.Black.copy(alpha = 0.10f * (1f - hop / r)),
        topLeft = Offset(cx - shadowW / 2f, ground - r * 0.07f),
        size = Size(shadowW, r * 0.2f),
    )

    rotate(degrees = tilt, pivot = Offset(cx, ground)) {
        // 몸. 왼쪽 위에서 빛을 받는 무광 공
        drawOval(
            brush = Brush.radialGradient(
                0f to DoranPalette.BodyLight,
                0.42f to DoranPalette.Body,
                1f to DoranPalette.BodyShade,
                center = Offset(cx - w * 0.2f, top + h * 0.26f),
                radius = r * 1.75f,
            ),
            topLeft = Offset(cx - w / 2f, top),
            size = Size(w, h),
        )
        // 바닥에서 올라오는 그늘
        drawOval(
            brush = Brush.verticalGradient(
                0.55f to Color.Transparent,
                1f to DoranPalette.BodyShade.copy(alpha = 0.55f),
                startY = top,
                endY = top + h,
            ),
            topLeft = Offset(cx - w / 2f, top),
            size = Size(w, h),
        )

        val eyeY = cy - r * 0.1f + gazeY * r * 0.12f
        val eyeShiftX = gazeX * r * 0.15f
        val blink = blinkOpen(t)
        for (side in listOf(-1f, 1f)) {
            val ex = cx + side * r * 0.34f * scaleX + eyeShiftX
            val ew = r * 0.17f * eyeScale
            if (mood == DoranMood.HAPPY) {
                drawArc(
                    color = DoranPalette.Eye,
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(ex - ew * 0.8f, eyeY - ew * 0.3f),
                    size = Size(ew * 1.6f, ew * 1.3f),
                    style = Stroke(width = r * 0.06f, cap = StrokeCap.Round),
                )
                continue
            }
            val eh = r * 0.26f * eyeScale * blink
            drawOval(DoranPalette.Eye, topLeft = Offset(ex - ew / 2f, eyeY - eh / 2f), size = Size(ew, maxOf(eh, r * 0.025f)))
            if (blink > 0.5f) {
                drawCircle(Color.White, radius = ew * 0.2f, center = Offset(ex - ew * 0.14f, eyeY - eh * 0.24f))
            }
            // 볼
            drawOval(
                DoranPalette.BodyShade.copy(alpha = 0.22f),
                topLeft = Offset(ex - r * 0.13f + side * r * 0.06f, eyeY + r * 0.2f),
                size = Size(r * 0.26f, r * 0.11f),
            )
        }

        val mouthX = cx + eyeShiftX * 0.6f
        val mouthY = eyeY + r * 0.3f
        if (mood == DoranMood.SPEAKING) {
            val mw = r * 0.16f
            val mh = r * (0.03f + 0.15f * speak)
            drawOval(DoranPalette.Eye, topLeft = Offset(mouthX - mw / 2f, mouthY - mh / 2f), size = Size(mw, mh))
        } else if (mood != DoranMood.THINKING) {
            drawArc(
                color = DoranPalette.Eye,
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(mouthX - r * 0.08f, mouthY - r * 0.07f),
                size = Size(r * 0.16f, r * 0.1f),
                style = Stroke(width = r * 0.035f, cap = StrokeCap.Round),
            )
        }
    }
}

/** 4.3초마다 한 번, 가끔 두 번 깜박인다. 1이면 뜬 눈 */
private fun blinkOpen(t: Float): Float {
    val cycle = 4.3f
    val phase = t % cycle
    val first = blinkCurve(phase)
    val second = if ((t / cycle).toInt() % 3 == 0) blinkCurve(phase - 0.28f) else 1f
    return minOf(first, second)
}

private fun blinkCurve(phase: Float): Float {
    if (phase < 0f || phase > BLINK_S) return 1f
    return abs(1f - 2f * phase / BLINK_S)
}

private const val BLINK_S = 0.16f
private const val CHEER_MS = 1_600L
