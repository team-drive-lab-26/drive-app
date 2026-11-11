package com.doran.app.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 도란 색. 화면은 무채색으로 두고, 색은 도란 몸과 선택된 것 하나에만 쓴다.
 * 바탕은 차 실내 천장재 같은 밝은 회색, 글자는 먹색.
 */
object DoranPalette {
    val Background = Color(0xFFF2F1EE)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF17181A)
    val InkSoft = Color(0xFF6A6D72)
    val Line = Color(0xFFDDDBD6)

    // 도란 몸. 무광 주황을 빛 받는 쪽, 몸통, 그늘 세 단계로 나눠 입체감을 낸다
    val BodyLight = Color(0xFFFFB27E)
    val Body = Color(0xFFFF7A32)
    val BodyShade = Color(0xFFC9531C)
    val Eye = Color(0xFF1C1B1A)
}

private val DoranColors = lightColorScheme(
    background = DoranPalette.Background,
    surface = DoranPalette.Surface,
    onBackground = DoranPalette.Ink,
    onSurface = DoranPalette.Ink,
    onSurfaceVariant = DoranPalette.InkSoft,
    outline = DoranPalette.Line,
    primary = DoranPalette.Ink,
    onPrimary = DoranPalette.Surface,
    secondary = DoranPalette.Body,
)

// 주행 자막 34sp, 본문 20sp 이상. 운전 중 한 번에 읽히는 크기
private val DoranType = Typography(
    displayMedium = TextStyle(fontSize = 34.sp, lineHeight = 44.sp, fontWeight = FontWeight.Medium),
    headlineMedium = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 20.sp, lineHeight = 30.sp),
    bodyMedium = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    labelLarge = TextStyle(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun DoranTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DoranColors, typography = DoranType, content = content)
}
