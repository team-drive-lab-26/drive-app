package com.doran.app.feature.driving

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.doran.app.core.designsystem.CALL_HINT
import com.doran.app.core.designsystem.CaptionBand
import com.doran.app.core.designsystem.DoranFace
import com.doran.app.core.designsystem.DoranPalette
import com.doran.app.core.designsystem.TappableDoran
import com.doran.app.core.designsystem.doranFeetOffset

/**
 * 주행 중 화면. 정차 때 펼쳐져 있던 여행 테이프가 자막 한 줄짜리 띠로 접힌다.
 * 도란은 띠 왼쪽 끝에 앉아 있고, 위쪽에 길 정보 한 줄만 둔다. 버튼, 지도, 카드는 없다.
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
        val band = 176.dp
        val bandTop = maxHeight * 0.54f
        val doran = minOf(maxHeight * 0.42f, 340.dp)
        if (routeLine.isNotBlank()) {
            Text(
                text = routeLine,
                style = MaterialTheme.typography.headlineMedium,
                color = DoranPalette.Ink,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 56.dp, top = 40.dp),
            )
        }
        CaptionBand(
            caption = caption,
            hint = CALL_HINT,
            height = band,
            leading = 56.dp,
            modifier = Modifier.offset(y = bandTop),
        )
        Box(Modifier.offset(x = 40.dp, y = bandTop - doranFeetOffset(doran))) {
            TappableDoran(face = face, onCall = onCall, onLongPress = onLongPress, modifier = Modifier.size(doran))
        }
    }
}
