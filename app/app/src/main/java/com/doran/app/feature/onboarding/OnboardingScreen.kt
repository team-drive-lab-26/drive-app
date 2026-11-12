package com.doran.app.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doran.app.core.designsystem.DoranCharacter
import com.doran.app.core.designsystem.DoranIcon
import com.doran.app.core.designsystem.DoranMood
import com.doran.app.core.designsystem.DoranPalette
import com.doran.app.core.designsystem.DoranPrimaryButton
import com.doran.app.core.designsystem.DoranText
import com.doran.app.core.designsystem.Glyph
import com.doran.app.core.designsystem.RoadScene
import com.doran.app.core.designsystem.Rule
import com.doran.app.core.designsystem.panel

private class Notice(val icon: DoranIcon, val title: String, val body: String)

private val NOTICES = listOf(
    Notice(DoranIcon.MIC, "마이크", "\"도란아\"로 시작한 말만 서버로 보내요. 부르기 전에 들린 말은 기기에서 버려요."),
    Notice(DoranIcon.PIN, "위치", "경로 주변 장소와 휴게소를 찾을 때만 써요."),
    Notice(DoranIcon.VOICE, "녹음", "목소리 파일은 이 기기에만 저장해요. 서버에는 언제 어디서 남겼는지만 보내요."),
)

/**
 * 처음 한 번만 보는 화면. 아직 아무것도 서 있지 않은 빈 앞길과 내 차 자리의 도란.
 * 왼쪽 위에 인사, 오른쪽 패널에서 마이크·위치·녹음 사용 범위를 알리고 동의를 받는다.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    Box(Modifier.fillMaxSize().background(DoranPalette.Background)) {
        RoadScene(horizon = 0.40f, orbSize = 170.dp, flow = true) {
            DoranCharacter(mood = DoranMood.IDLE, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.padding(start = 56.dp, top = 48.dp).width(560.dp)) {
            Text("안녕, 나는 도란이야", style = DoranText.display, color = DoranPalette.Ink)
            Spacer(Modifier.height(16.dp))
            Text(
                "가는 길에 들를 곳을 같이 찾고, 들른 곳에서 남긴 목소리를 지나온 길 위에 모아 둘게.",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 24.sp, lineHeight = 36.sp),
                color = DoranPalette.InkSoft,
            )
        }
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 40.dp, end = 32.dp)
                .width(560.dp)
                .panel()
                .padding(horizontal = 32.dp, vertical = 20.dp),
        ) {
            NOTICES.forEachIndexed { index, notice ->
                if (index > 0) Rule()
                Row(Modifier.padding(vertical = 18.dp)) {
                    Box(Modifier.size(52.dp).background(DoranPalette.Line, CircleShape), contentAlignment = Alignment.Center) {
                        Glyph(notice.icon, DoranPalette.Ink, size = 26.dp)
                    }
                    Spacer(Modifier.width(18.dp))
                    Column {
                        Text(notice.title, style = MaterialTheme.typography.titleLarge, color = DoranPalette.Ink)
                        Spacer(Modifier.height(2.dp))
                        Text(notice.body, style = MaterialTheme.typography.bodyLarge, color = DoranPalette.InkSoft)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            DoranPrimaryButton(text = "동의하고 시작하기", onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp))
        }
    }
}
