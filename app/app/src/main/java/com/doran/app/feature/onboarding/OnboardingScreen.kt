package com.doran.app.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.doran.app.core.designsystem.DoranCharacter
import com.doran.app.core.designsystem.DoranMood
import com.doran.app.core.designsystem.DoranPalette
import com.doran.app.core.designsystem.DoranPrimaryButton
import com.doran.app.core.designsystem.RoadScene
import com.doran.app.core.designsystem.Rule

private val NOTICES = listOf(
    "마이크" to "\"도란아\"로 시작한 말만 서버로 보내요. 부르기 전에 들린 말은 기기에서 버려요.",
    "위치" to "경로 주변 장소와 휴게소를 찾을 때만 써요.",
    "녹음" to "목소리 파일은 이 기기에만 저장해요. 서버에는 언제 어디서 남겼는지만 보내요.",
)

/**
 * 처음 한 번만 보는 화면. 아직 아무것도 서 있지 않은 빈 앞길과 내 차 자리의 도란.
 * 왼쪽 위에 인사, 오른쪽 패널에서 마이크·위치·녹음 사용 범위를 알리고 동의를 받는다.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    Box(Modifier.fillMaxSize().background(DoranPalette.Background)) {
        RoadScene(horizon = 0.46f, orbSize = 170.dp) {
            DoranCharacter(mood = DoranMood.IDLE, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.padding(start = 56.dp, top = 56.dp).width(440.dp)) {
            Text("안녕, 나는 도란이야", style = MaterialTheme.typography.headlineLarge, color = DoranPalette.Ink)
            Spacer(Modifier.height(12.dp))
            Text(
                "가는 길에 들를 곳을 같이 찾고, 들른 곳에서 남긴 목소리를 지나온 길 위에 모아 둘게.",
                style = MaterialTheme.typography.bodyLarge,
                color = DoranPalette.InkSoft,
            )
        }
        val shape = RoundedCornerShape(22.dp)
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 56.dp, end = 40.dp)
                .width(520.dp)
                .background(DoranPalette.Surface, shape)
                .border(1.dp, DoranPalette.Line, shape)
                .padding(horizontal = 28.dp, vertical = 20.dp),
        ) {
            NOTICES.forEachIndexed { index, (title, body) ->
                if (index > 0) Rule()
                Row(Modifier.padding(vertical = 16.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = DoranPalette.Ink, modifier = Modifier.width(88.dp))
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = DoranPalette.InkSoft)
                }
            }
            Spacer(Modifier.height(12.dp))
            DoranPrimaryButton(text = "동의하고 시작하기", onClick = onDone)
        }
    }
}
