package com.doran.app.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp
import com.doran.app.R

/**
 * 도란 색. 화면은 차 앞 도로를 펼친 장면이다. 차 실내와 밤 귀갓길에 눈부시지 않게 어두운 바탕을 쓴다.
 * 색은 도란(주황 빛)과 경로선, 지금 고를 것에만 쓴다. 가족 목소리 핀은 사람마다 정한 색.
 */
object DoranPalette {
    val Background = Color(0xFF0B1018)
    val Ridge = Color(0xFF121A26)
    val Road = Color(0xFF161E2A)
    val RoadEdge = Color(0xFF2E3A4C)
    val LaneMark = Color(0xFF3A475B)
    val Surface = Color(0xFF151C27)
    val SurfaceHigh = Color(0xFF1B2431)
    val Line = Color(0xFF273244)
    val LineStrong = Color(0xFF3A475B)
    val Ink = Color(0xFFEEF2F7)
    val InkSoft = Color(0xFFA5B0C1)
    val InkMute = Color(0xFF7F8A9C)

    // 도란과 경로선. 지금 고를 것도 이 색
    val Accent = Color(0xFFFF7A32)
    val AccentSoft = Color(0xFFFFB27E)
    val AccentDeep = Color(0xFFC9531C)
    val OnAccent = Color(0xFF14100D)

    // 예전 이름. 디버그 화면이 쓴다
    val Body = Accent
    val Head = Accent
}

/** 가족마다 고정 색. 같은 사람은 어느 화면에서나 같은 색이다 */
object FamilyRibbon {
    private val defaults = mapOf(
        "엄마" to Color(0xFFFF8FA8),
        "아빠" to Color(0xFF7CC6FF),
        "아이" to Color(0xFFFFD45E),
    )
    private val extra = listOf(Color(0xFF8FE3B8), Color(0xFFC7AEFF), Color(0xFFFFB27A), Color(0xFF9EE6F0))

    fun colorFor(name: String): Color = defaults[name] ?: extra[Math.floorMod(name.hashCode(), extra.size)]
}

val Suit = FontFamily(
    Font(R.font.suit_regular, FontWeight.Normal),
    Font(R.font.suit_semibold, FontWeight.Medium),
    Font(R.font.suit_semibold, FontWeight.SemiBold),
    Font(R.font.suit_extrabold, FontWeight.Bold),
    Font(R.font.suit_extrabold, FontWeight.ExtraBold),
)

private const val TABULAR = "tnum"

// 한글이 글자 단위로 끊겨 한 글자만 다음 줄에 남지 않게 어절 단위로 끊는다
private val Phrase = LineBreak(LineBreak.Strategy.Balanced, LineBreak.Strictness.Strict, LineBreak.WordBreak.Phrase)

object DoranText {
    /** 시각, 거리, 초. 자리가 흔들리지 않게 고정폭 숫자 */
    val number = TextStyle(fontFamily = Suit, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR)
    val countdown = TextStyle(fontFamily = Suit, fontSize = 112.sp, lineHeight = 112.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = TABULAR, letterSpacing = (-2).sp)

    /** 길가 표지판 이름. 거리에 따라 크기를 줄여 쓴다 */
    val sign = TextStyle(fontFamily = Suit, fontSize = 27.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
    val signSub = TextStyle(fontFamily = Suit, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = TABULAR)
}

private val DoranColors = darkColorScheme(
    background = DoranPalette.Background,
    surface = DoranPalette.Surface,
    surfaceContainerHighest = DoranPalette.SurfaceHigh,
    onBackground = DoranPalette.Ink,
    onSurface = DoranPalette.Ink,
    onSurfaceVariant = DoranPalette.InkSoft,
    outline = DoranPalette.LineStrong,
    outlineVariant = DoranPalette.Line,
    primary = DoranPalette.Accent,
    onPrimary = DoranPalette.OnAccent,
    secondary = DoranPalette.Accent,
)

// 주행 자막 34sp 이상, 본문 18sp 이상. 운전 중 한 번에 읽히는 크기
private val DoranType = Typography(
    displayMedium = TextStyle(fontFamily = Suit, fontSize = 40.sp, lineHeight = 50.sp, fontWeight = FontWeight.Bold, lineBreak = Phrase),
    headlineLarge = TextStyle(fontFamily = Suit, fontSize = 36.sp, lineHeight = 46.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, lineBreak = Phrase),
    headlineMedium = TextStyle(fontFamily = Suit, fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp, lineBreak = Phrase),
    titleLarge = TextStyle(fontFamily = Suit, fontSize = 23.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = Suit, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = Suit, fontSize = 20.sp, lineHeight = 30.sp, lineBreak = Phrase),
    bodyMedium = TextStyle(fontFamily = Suit, fontSize = 18.sp, lineHeight = 26.sp, lineBreak = Phrase),
    labelLarge = TextStyle(fontFamily = Suit, fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
)

@Composable
fun DoranTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DoranColors, typography = DoranType, content = content)
}
