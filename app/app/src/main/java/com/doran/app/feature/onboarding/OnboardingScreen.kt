package com.doran.app.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.doran.app.core.designsystem.DoranCharacter
import com.doran.app.core.designsystem.DoranMood
import com.doran.app.core.designsystem.DoranPalette
import com.doran.app.core.designsystem.DoranPrimaryButton
import com.doran.app.core.designsystem.Rule
import com.doran.app.core.designsystem.TapeStrip
import com.doran.app.core.designsystem.doranFeetOffset

private val NOTICES = listOf(
    "마이크" to "\"도란아\"로 시작한 말만 서버로 보내요. 부르기 전에 들린 말은 기기에서 버려요.",
    "위치" to "경로 주변 장소와 휴게소를 찾을 때만 써요.",
    "녹음" to "목소리 파일은 이 기기에만 저장해요. 서버에는 언제 어디서 남겼는지만 보내요.",
)

/**
 * 처음 한 번만 보는 화면. 새 테이프를 끼우는 장면이다.
 * 아래에 아직 비어 있는 테이프가 깔리고 도란이 헤드 위에 앉아 있다. 오른쪽에서 마이크·위치·녹음 사용 범위를 알리고 동의를 받는다.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(DoranPalette.Background),
    ) {
        val band = 156.dp
        val bandTop = maxHeight - band - 44.dp - 36.dp
        val headAt = 0.2f
        val doran = minOf(bandTop * 0.8f, 340.dp)

        TapeStrip(
            entries = emptyList(),
            playhead = headAt,
            bandHeight = band,
            endTitle = "첫 여행",
            endSub = "출발하면 여기서부터 녹음돼",
            modifier = Modifier.offset(y = bandTop),
        )
        Box(Modifier.offset(x = maxWidth * headAt - doran / 2, y = bandTop - doranFeetOffset(doran))) {
            DoranCharacter(mood = DoranMood.IDLE, modifier = Modifier.size(doran))
        }

        Column(
            Modifier
                .offset(x = maxWidth * 0.42f)
                .width(maxWidth * 0.58f - 64.dp)
                .padding(top = 56.dp),
        ) {
            Text("안녕, 나는 도란이야", style = MaterialTheme.typography.headlineLarge, color = DoranPalette.Ink)
            Spacer(Modifier.height(12.dp))
            Text(
                "가는 길에 들를 곳을 같이 찾고, 들른 곳에서 남긴 목소리를 여행 테이프에 모아 둘게.",
                style = MaterialTheme.typography.bodyLarge,
                color = DoranPalette.InkSoft,
            )
            Spacer(Modifier.height(28.dp))
            NOTICES.forEachIndexed { index, (title, body) ->
                if (index > 0) Rule()
                Row(Modifier.padding(vertical = 16.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = DoranPalette.Ink, modifier = Modifier.width(96.dp))
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = DoranPalette.InkSoft)
                }
            }
            Spacer(Modifier.height(28.dp))
            DoranPrimaryButton(text = "동의하고 시작하기", onClick = onDone)
        }
    }
}
