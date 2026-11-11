package com.doran.app.feature.driving

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.doran.app.core.designsystem.CALL_HINT
import com.doran.app.core.designsystem.DoranButtonShape
import com.doran.app.core.designsystem.DoranFace
import com.doran.app.core.designsystem.DoranMood
import com.doran.app.core.designsystem.DoranPalette
import com.doran.app.core.designsystem.DoranText
import com.doran.app.core.designsystem.RoadScene
import com.doran.app.core.designsystem.TappableDoran

/**
 * 주행 중 화면. 정차 때와 같은 앞길이지만 표지판, 패널은 모두 걷히고 길과 경로선만 남는다.
 * 하늘 자리에 자막 한 줄, 내 차 자리에 도란. 말하거나 듣는 동안에만 오른쪽 아래에 작은 정지 아이콘이 붙는다.
 */
@Composable
fun DrivingScreen(
    face: DoranFace,
    caption: String,
    routeLine: String,
    onCall: () -> Unit,
    onStop: () -> Unit,
    onLongPress: () -> Unit,
) {
    val busy = face.mood != DoranMood.IDLE && face.mood != DoranMood.HAPPY
    Box(
        Modifier
            .fillMaxSize()
            .background(DoranPalette.Background),
    ) {
        // 정차 화면과 같은 지평선·도란 크기라 기어가 바뀌어도 길은 그 자리에 있고 표지판만 걷힌다
        RoadScene(horizon = 0.40f, orbSize = 160.dp) {
            TappableDoran(face = face, onCall = onCall, onLongPress = onLongPress, still = true, modifier = Modifier.fillMaxSize())
        }
        if (routeLine.isNotBlank()) {
            Text(
                routeLine,
                style = DoranText.number.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize),
                color = DoranPalette.InkSoft,
                modifier = Modifier.padding(start = 48.dp, top = 32.dp),
            )
        }
        Text(
            caption.ifBlank { CALL_HINT },
            style = MaterialTheme.typography.displayMedium,
            color = if (caption.isBlank()) DoranPalette.InkSoft else DoranPalette.Ink,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 80.dp, end = 80.dp, top = 168.dp),
        )
        if (busy) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 40.dp, bottom = 40.dp)
                    .size(72.dp)
                    .clip(DoranButtonShape)
                    .border(2.dp, DoranPalette.LineStrong, DoranButtonShape)
                    .clickable(onClick = onStop)
                    .semantics { contentDescription = "멈추기" },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(24.dp)) {
                    drawRoundRect(DoranPalette.Ink, cornerRadius = CornerRadius(4.dp.toPx()))
                }
            }
        }
    }
}
