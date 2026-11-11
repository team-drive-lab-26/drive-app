package com.doran.app.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.doran.app.R

/**
 * 도란 색. 여행 한 번을 카세트테이프 한 개로 본다.
 * 바탕은 창밖 하늘색, 화면을 가로지르는 띠는 자기 테이프 갈색, 글자는 남색 먹.
 * 색은 도란(귤색), 가족마다 하나씩 정한 리본 색, 선택된 것에만 쓴다.
 */
object DoranPalette {
    val Background = Color(0xFFCFE4EF)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF14202B)
    val InkSoft = Color(0xFF3D5364)
    val Line = Color(0xFFA6C2D2)
    val LineOnPaper = Color(0xFFDCE5EB)

    // 테이프. 녹음된 쪽은 짙고, 아직 녹음하지 않은 앞길은 한 단계 밝다
    val Tape = Color(0xFF2A221E)
    val TapeBlank = Color(0xFF433832)
    val TapeEdge = Color(0xFF5A4B43)
    val TapeText = Color(0xFFF7F2EC)
    val TapeTextSoft = Color(0xFFCBBFB4)

    // 재생 헤드와 선택 표시. 도란 몸과 같은 계열
    val Head = Color(0xFFFF7A32)

    // 도란 몸. 무광 주황을 빛 받는 쪽, 몸통, 그늘 세 단계로 나눠 입체감을 낸다
    val BodyLight = Color(0xFFFFB27E)
    val Body = Color(0xFFFF7A32)
    val BodyShade = Color(0xFFC9531C)
    val Eye = Color(0xFF1C1B1A)
    val Stem = Color(0xFF4F6B2E)
    val Leaf = Color(0xFF4E8A3A)
    val LeafLight = Color(0xFF7DB25A)
}

/** 가족마다 고정 리본 색. 같은 사람은 어느 화면에서나 같은 색이다 */
object FamilyRibbon {
    private val defaults = mapOf(
        "엄마" to Color(0xFFFF8FA8),
        "아빠" to Color(0xFF7CC6FF),
        "아이" to Color(0xFFFFD45E),
    )
    private val extra = listOf(
        Color(0xFF8FE3B8),
        Color(0xFFC7AEFF),
        Color(0xFFFFB27A),
        Color(0xFF9EE6F0),
    )

    fun colorFor(name: String): Color = defaults[name] ?: extra[Math.floorMod(name.hashCode(), extra.size)]
}

val Suit = FontFamily(
    Font(R.font.suit_regular, FontWeight.Normal),
    Font(R.font.suit_semibold, FontWeight.Medium),
    Font(R.font.suit_semibold, FontWeight.SemiBold),
    Font(R.font.suit_extrabold, FontWeight.Bold),
    Font(R.font.suit_extrabold, FontWeight.ExtraBold),
)

/** 테이프 라벨에 펜으로 적은 글씨. 장소 이름과 남긴 사람에만 쓴다 */
val PenHand = FontFamily(Font(R.font.nanum_pen, FontWeight.Normal))

private const val TABULAR = "tnum"

object DoranText {
    /** 테이프 라벨 손글씨 */
    val label = TextStyle(fontFamily = PenHand, fontSize = 30.sp, lineHeight = 32.sp)
    val labelSmall = TextStyle(fontFamily = PenHand, fontSize = 26.sp, lineHeight = 28.sp)

    /** 시각, 남은 시간, 초. 자리가 흔들리지 않게 고정폭 숫자 */
    val time = TextStyle(fontFamily = Suit, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR)
    val countdown = TextStyle(fontFamily = Suit, fontSize = 120.sp, lineHeight = 120.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = TABULAR, letterSpacing = (-2).sp)
    val trackNo = TextStyle(fontFamily = Suit, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = TABULAR)
}

private val DoranColors = lightColorScheme(
    background = DoranPalette.Background,
    surface = DoranPalette.Surface,
    onBackground = DoranPalette.Ink,
    onSurface = DoranPalette.Ink,
    onSurfaceVariant = DoranPalette.InkSoft,
    outline = DoranPalette.Line,
    outlineVariant = DoranPalette.LineOnPaper,
    primary = DoranPalette.Ink,
    onPrimary = DoranPalette.Surface,
    secondary = DoranPalette.Body,
    surfaceContainerHighest = DoranPalette.Surface,
)

// 주행 자막 34sp 이상, 본문 18sp 이상. 운전 중 한 번에 읽히는 크기
private val DoranType = Typography(
    displayMedium = TextStyle(fontFamily = Suit, fontSize = 38.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold),
    headlineLarge = TextStyle(fontFamily = Suit, fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Suit, fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold),
    titleLarge = TextStyle(fontFamily = Suit, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = Suit, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = Suit, fontSize = 20.sp, lineHeight = 30.sp),
    bodyMedium = TextStyle(fontFamily = Suit, fontSize = 18.sp, lineHeight = 26.sp),
    labelLarge = TextStyle(fontFamily = Suit, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun DoranTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DoranColors, typography = DoranType, content = content)
}
