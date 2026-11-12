package com.doran.app.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp
import com.doran.app.R

/**
 * 도란 색. 화면은 차 앞 도로를 펼친 장면이다.
 * 밤(기본)은 어두운 하늘과 길, 낮은 밝은 하늘과 아스팔트 길. 구조는 같고 색만 바뀐다.
 * 색은 도란(주황 빛)과 경로선, 지금 고를 것에만 쓴다. 가족 목소리 핀은 사람마다 정한 색.
 */
object DoranPalette {
    /** 낮 화면인지. DaylightSource가 정한 값을 MainActivity가 넣는다 */
    var isDay by mutableStateOf(false)

    private fun pick(night: Long, day: Long) = Color(if (isDay) day else night)

    val Background get() = pick(0xFF0B1018, 0xFFE8EDF2)
    val Ridge get() = pick(0xFF121A26, 0xFFD2DAE2)
    val Ground get() = pick(0xFF0F1520, 0xFFDDE3E9)
    val Road get() = pick(0xFF161E2A, 0xFF4A5361)
    val RoadEdge get() = pick(0xFF2E3A4C, 0xFFF4F6F8)
    val LaneMark get() = pick(0xFF3A475B, 0xFFE3E8ED)
    val Surface get() = pick(0xFF151C27, 0xFFFFFFFF)
    val SurfaceHigh get() = pick(0xFF1B2431, 0xFFFFFFFF)
    val Line get() = pick(0xFF273244, 0xFFD3DAE2)
    val LineStrong get() = pick(0xFF3A475B, 0xFFB7C1CC)
    val Ink get() = pick(0xFFEEF2F7, 0xFF10151C)
    val InkSoft get() = pick(0xFFA5B0C1, 0xFF465261)
    val InkMute get() = pick(0xFF7F8A9C, 0xFF5E6977)

    // 도란과 경로선. 지금 고를 것도 이 색
    val Accent get() = pick(0xFFFF7A32, 0xFFF26A1F)
    val AccentSoft = Color(0xFFFFB27E)
    val AccentDeep = Color(0xFFC9531C)
    val OnAccent = Color(0xFF14100D)

    // 예전 이름. 디버그 화면이 쓴다
    val Body get() = Accent
    val Head get() = Accent
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
private val Korean = LocaleList("ko-KR")

object DoranText {
    /** 시각, 거리, 초. 자리가 흔들리지 않게 고정폭 숫자 */
    val number = TextStyle(localeList = Korean, fontFamily = Suit, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR)
    val countdown = TextStyle(localeList = Korean, fontFamily = Suit, fontSize = 112.sp, lineHeight = 112.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = TABULAR, letterSpacing = (-2).sp)

    /** 길가 표지판 이름. 거리에 따라 크기를 줄여 쓴다 */
    val sign = TextStyle(localeList = Korean, fontFamily = Suit, fontSize = 27.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
    val signSub = TextStyle(localeList = Korean, fontFamily = Suit, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = TABULAR)
}

private fun doranColors() = if (DoranPalette.isDay) {
    lightColorScheme(
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
} else {
    darkColorScheme(
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
}

// 주행 자막 34sp 이상, 본문 18sp 이상. 운전 중 한 번에 읽히는 크기
private val DoranType = Typography(
    displayMedium = TextStyle(localeList = Korean, fontFamily = Suit, fontSize = 40.sp, lineHeight = 50.sp, fontWeight = FontWeight.Bold, lineBreak = Phrase),
    headlineLarge = TextStyle(localeList = Korean, fontFamily = Suit, fontSize = 36.sp, lineHeight = 46.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, lineBreak = Phrase),
    headlineMedium = TextStyle(localeList = Korean, fontFamily = Suit, fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp, lineBreak = Phrase),
    titleLarge = TextStyle(localeList = Korean, fontFamily = Suit, fontSize = 23.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, lineBreak = Phrase),
    titleMedium = TextStyle(localeList = Korean, fontFamily = Suit, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, lineBreak = Phrase),
    bodyLarge = TextStyle(localeList = Korean, fontFamily = Suit, fontSize = 20.sp, lineHeight = 30.sp, lineBreak = Phrase),
    bodyMedium = TextStyle(localeList = Korean, fontFamily = Suit, fontSize = 18.sp, lineHeight = 26.sp, lineBreak = Phrase),
    labelLarge = TextStyle(localeList = Korean, fontFamily = Suit, fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
)

@Composable
fun DoranTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = doranColors(), typography = DoranType, content = content)
}
