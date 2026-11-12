package com.doran.app.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 앱에서 쓰는 아이콘. 글리프 대신 같은 굵기의 선으로 직접 그린다 */
enum class DoranIcon { SIGNPOST, NOTE, SLIDERS, PERSON, PLAY, PAUSE, NEXT, MIC, PIN, VOICE }

@Composable
fun Glyph(icon: DoranIcon, color: Color, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Canvas(modifier.size(size)) { drawIcon(icon, color) }
}

private fun DrawScope.drawIcon(icon: DoranIcon, color: Color) {
    val s = size.minDimension
    val line = Stroke(width = s * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(x: Float, y: Float) = Offset(x * s, y * s)
    when (icon) {
        // 앨범: 길가 표지판
        DoranIcon.SIGNPOST -> {
            drawRoundRect(color, topLeft = p(0.14f, 0.12f), size = Size(s * 0.72f, s * 0.38f), cornerRadius = CornerRadius(s * 0.08f), style = line)
            drawLine(color, p(0.5f, 0.5f), p(0.5f, 0.9f), strokeWidth = line.width, cap = StrokeCap.Round)
            drawLine(color, p(0.3f, 0.9f), p(0.7f, 0.9f), strokeWidth = line.width, cap = StrokeCap.Round)
        }
        DoranIcon.NOTE -> {
            drawCircle(color, s * 0.13f, p(0.32f, 0.74f), style = line)
            drawCircle(color, s * 0.13f, p(0.76f, 0.64f), style = line)
            val path = Path().apply {
                moveTo(0.45f * s, 0.74f * s)
                lineTo(0.45f * s, 0.18f * s)
                lineTo(0.89f * s, 0.10f * s)
                lineTo(0.89f * s, 0.64f * s)
            }
            drawPath(path, color, style = line)
            drawLine(color, p(0.45f, 0.32f), p(0.89f, 0.24f), strokeWidth = line.width, cap = StrokeCap.Round)
        }
        DoranIcon.SLIDERS -> {
            listOf(0.28f to 0.64f, 0.72f to 0.34f).forEach { (y, knob) ->
                drawLine(color, p(0.1f, y), p(0.9f, y), strokeWidth = line.width, cap = StrokeCap.Round)
                drawCircle(color, s * 0.12f, p(knob, y))
            }
        }
        DoranIcon.PERSON -> {
            drawCircle(color, s * 0.17f, p(0.5f, 0.3f), style = line)
            val body = Path().apply {
                moveTo(0.16f * s, 0.9f * s)
                cubicTo(0.16f * s, 0.6f * s, 0.84f * s, 0.6f * s, 0.84f * s, 0.9f * s)
            }
            drawPath(body, color, style = line)
        }
        DoranIcon.PLAY -> {
            val tri = Path().apply {
                moveTo(0.28f * s, 0.16f * s)
                lineTo(0.84f * s, 0.5f * s)
                lineTo(0.28f * s, 0.84f * s)
                close()
            }
            drawPath(tri, color)
            drawPath(tri, color, style = Stroke(width = s * 0.08f, join = StrokeJoin.Round))
        }
        DoranIcon.PAUSE -> {
            drawRoundRect(color, topLeft = p(0.22f, 0.16f), size = Size(s * 0.18f, s * 0.68f), cornerRadius = CornerRadius(s * 0.05f))
            drawRoundRect(color, topLeft = p(0.60f, 0.16f), size = Size(s * 0.18f, s * 0.68f), cornerRadius = CornerRadius(s * 0.05f))
        }
        DoranIcon.NEXT -> {
            val tri = Path().apply {
                moveTo(0.16f * s, 0.18f * s)
                lineTo(0.66f * s, 0.5f * s)
                lineTo(0.16f * s, 0.82f * s)
                close()
            }
            drawPath(tri, color)
            drawPath(tri, color, style = Stroke(width = s * 0.07f, join = StrokeJoin.Round))
            drawRoundRect(color, topLeft = p(0.70f, 0.18f), size = Size(s * 0.13f, s * 0.64f), cornerRadius = CornerRadius(s * 0.05f))
        }
        DoranIcon.MIC -> {
            drawRoundRect(color, topLeft = p(0.36f, 0.08f), size = Size(s * 0.28f, s * 0.5f), cornerRadius = CornerRadius(s * 0.14f), style = line)
            val cup = Path().apply {
                moveTo(0.2f * s, 0.44f * s)
                cubicTo(0.2f * s, 0.8f * s, 0.8f * s, 0.8f * s, 0.8f * s, 0.44f * s)
            }
            drawPath(cup, color, style = line)
            drawLine(color, p(0.5f, 0.72f), p(0.5f, 0.92f), strokeWidth = line.width, cap = StrokeCap.Round)
        }
        DoranIcon.PIN -> {
            val pin = Path().apply {
                moveTo(0.5f * s, 0.92f * s)
                cubicTo(0.2f * s, 0.6f * s, 0.16f * s, 0.46f * s, 0.16f * s, 0.38f * s)
                cubicTo(0.16f * s, 0.18f * s, 0.32f * s, 0.06f * s, 0.5f * s, 0.06f * s)
                cubicTo(0.68f * s, 0.06f * s, 0.84f * s, 0.18f * s, 0.84f * s, 0.38f * s)
                cubicTo(0.84f * s, 0.46f * s, 0.8f * s, 0.6f * s, 0.5f * s, 0.92f * s)
                close()
            }
            drawPath(pin, color, style = line)
            drawCircle(color, s * 0.1f, p(0.5f, 0.38f))
        }
        // 목소리: 길이가 다른 막대 다섯
        DoranIcon.VOICE -> {
            listOf(0.3f, 0.6f, 0.9f, 0.55f, 0.35f).forEachIndexed { i, k ->
                val x = (0.14f + i * 0.18f) * s
                drawLine(color, Offset(x, s * (0.5f - k * 0.4f)), Offset(x, s * (0.5f + k * 0.4f)), strokeWidth = line.width, cap = StrokeCap.Round)
            }
        }
    }
}

/** 배터리. 남은 만큼 안을 채운다 */
@Composable
fun BatteryIcon(percent: Int, color: Color, modifier: Modifier = Modifier, width: Dp = 34.dp) {
    Canvas(modifier.size(width = width, height = width * 0.56f)) {
        val sw = size.height * 0.13f
        val bodyW = size.width * 0.88f
        drawRoundRect(color, topLeft = Offset(sw / 2f, sw / 2f), size = Size(bodyW - sw, size.height - sw), cornerRadius = CornerRadius(size.height * 0.2f), style = Stroke(sw))
        drawRoundRect(color, topLeft = Offset(bodyW + sw * 0.2f, size.height * 0.32f), size = Size(size.width - bodyW - sw * 0.2f, size.height * 0.36f), cornerRadius = CornerRadius(sw))
        val inner = (bodyW - sw * 3.2f) * (percent.coerceIn(0, 100) / 100f)
        if (inner > 0f) {
            drawRoundRect(color, topLeft = Offset(sw * 1.6f, sw * 1.6f), size = Size(inner, size.height - sw * 3.2f), cornerRadius = CornerRadius(sw * 0.5f))
        }
    }
}
