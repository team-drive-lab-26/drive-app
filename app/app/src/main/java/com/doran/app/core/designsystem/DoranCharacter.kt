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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** 도란이 지금 하고 있는 일. 마이크 아이콘 대신 도란의 모양과 움직임으로 보여 준다 */
enum class DoranMood { IDLE, LISTENING, THINKING, SPEAKING, HAPPY }

/**
 * 도란. 얼굴 없는 주황 빛 원 하나다.
 * 대기: 천천히 숨 쉬듯 커졌다 작아진다. 듣는 중: 운전석 쪽으로 쏠리고 마이크 소리만큼 가장자리가 출렁인다.
 * 생각 중: 가장자리를 따라 빛이 돈다. 말하는 중: 목소리 크기만큼 물결이 바깥으로 퍼진다.
 * [cheerKey]가 바뀌면 한 번 크게 맥박친다(경유지 추가, 녹음 저장). [still]이면 대기 중 숨쉬기를 멈춘다(주행 화면).
 */
@Composable
fun DoranCharacter(
    mood: DoranMood,
    modifier: Modifier = Modifier,
    level: Float = 0f,
    cheerKey: Long = 0L,
    still: Boolean = false,
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
    val voice by animateFloatAsState(level.coerceIn(0f, 1f), tween(90), label = "level")
    val think by animateFloatAsState(if (shown == DoranMood.THINKING) 1f else 0f, tween(260), label = "think")

    Canvas(modifier) {
        drawOrb(shown, time, lean, voice, think, still)
    }
}

private fun DrawScope.drawOrb(mood: DoranMood, t: Float, lean: Float, level: Float, think: Float, still: Boolean) {
    val base = size.minDimension * 0.31f
    val px = density
    val speak = when (mood) {
        // 목소리가 안 들어오는 엔진(안드로이드 기본 음성)이면 말하는 리듬을 흉내 낸다
        DoranMood.SPEAKING -> maxOf(level, 0.25f + 0.45f * abs(sin(t * 8.7f)) * abs(sin(t * 2.9f)))
        else -> 0f
    }
    val breath = if (still && mood == DoranMood.IDLE) 1f else 1f + 0.025f * sin(t * 1.6f)
    val pulse = if (mood == DoranMood.HAPPY) 1f + 0.09f * abs(sin(t * 6.5f)) else 1f
    val r = base * breath * pulse
    // 운전석(왼쪽)으로 쏠린다
    val c = Offset(size.width / 2f - lean * base * 0.18f, size.height / 2f)

    // 원 바깥으로 아주 옅게 번지는 빛
    drawCircle(
        Brush.radialGradient(listOf(DoranPalette.Accent.copy(alpha = 0.22f), Color.Transparent), center = c, radius = r * 1.6f),
        radius = r * 1.6f,
        center = c,
    )

    // 말하는 중: 바깥으로 퍼지는 물결 세 겹
    if (speak > 0f) {
        for (i in 0 until 3) {
            val phase = (t * 0.9f + i / 3f) % 1f
            drawCircle(
                DoranPalette.Accent.copy(alpha = (1f - phase) * 0.55f * speak.coerceIn(0.3f, 1f)),
                radius = r * (1.04f + 0.5f * phase),
                center = c,
                style = Stroke(width = 3f * px),
            )
        }
    }
    if (mood == DoranMood.HAPPY) {
        val phase = (t * 1.4f) % 1f
        drawCircle(
            DoranPalette.AccentSoft.copy(alpha = (1f - phase) * 0.7f),
            radius = r * (1.05f + 0.6f * phase),
            center = c,
            style = Stroke(width = 4f * px),
        )
    }

    // 몸. 가장자리를 상태에 따라 흔든다
    val path = Path()
    val n = 120
    for (k in 0..n) {
        val a = (k.toFloat() / n) * 2f * PI.toFloat()
        val wobble = when (mood) {
            DoranMood.LISTENING -> (0.03f + 0.11f * level) * (0.6f * sin(3 * a + t * 5f) + 0.4f * sin(5 * a - t * 7f)) + 0.012f * sin(4 * a + t * 3f)
            DoranMood.SPEAKING -> 0.05f * speak * sin(6 * a + t * 9f)
            DoranMood.THINKING -> 0.012f * sin(2 * a - t * 4f)
            else -> if (still) 0f else 0.008f * sin(3 * a + t * 1.2f)
        }
        val rr = r * (1f + wobble)
        val x = c.x + rr * cos(a)
        val y = c.y + rr * sin(a)
        if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(
        path,
        Brush.radialGradient(
            listOf(DoranPalette.AccentSoft, DoranPalette.Accent, DoranPalette.AccentDeep),
            center = Offset(c.x - r * 0.32f, c.y - r * 0.38f),
            radius = r * 1.7f,
        ),
    )
    // 윗면 반사광
    drawOval(Color.White.copy(alpha = 0.16f), topLeft = Offset(c.x - r * 0.55f, c.y - r * 0.78f), size = Size(r * 0.75f, r * 0.42f))

    // 생각 중: 가장자리를 따라 도는 빛
    if (think > 0f) {
        drawArc(
            DoranPalette.Ink.copy(alpha = 0.85f * think),
            startAngle = (t * 260f) % 360f,
            sweepAngle = 80f,
            useCenter = false,
            topLeft = Offset(c.x - r * 1.16f, c.y - r * 1.16f),
            size = Size(r * 2.32f, r * 2.32f),
            style = Stroke(width = 4f * px, cap = StrokeCap.Round),
        )
    }
}

private const val CHEER_MS = 1_600L
