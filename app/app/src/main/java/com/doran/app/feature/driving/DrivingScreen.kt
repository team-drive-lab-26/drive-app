package com.doran.app.feature.driving

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.doran.app.core.designsystem.DoranFace
import com.doran.app.core.designsystem.DoranPalette
import com.doran.app.core.designsystem.DoranStage

/**
 * 주행 중 화면. 버튼 없이 도란과 자막 한 줄, 위쪽에 길 정보 한 줄만 둔다.
 * 말은 "도란아"로 걸고, 도란을 눌러도 듣는다.
 */
@Composable
fun DrivingScreen(
    face: DoranFace,
    caption: String,
    routeLine: String,
    onCall: () -> Unit,
    onLongPress: () -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(DoranPalette.Background),
    ) {
        if (routeLine.isNotBlank()) {
            Text(
                text = routeLine,
                style = MaterialTheme.typography.titleLarge,
                color = DoranPalette.InkSoft,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 40.dp, top = 32.dp),
            )
        }
        DoranStage(
            face = face,
            caption = caption,
            onCall = onCall,
            onLongPress = onLongPress,
            characterSize = minOf(maxHeight * 0.58f, maxWidth * 0.4f),
            modifier = Modifier.align(Alignment.Center).padding(bottom = 24.dp),
        )
    }
}
