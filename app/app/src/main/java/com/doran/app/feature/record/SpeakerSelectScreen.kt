package com.doran.app.feature.record

import androidx.compose.runtime.Composable
import com.doran.app.core.designsystem.PlaceholderLink
import com.doran.app.core.designsystem.PlaceholderScreen

@Composable
fun SpeakerSelectScreen(
    onDone: () -> Unit,
) {
    PlaceholderScreen(
        title = "누가 말했나요?",
        links = listOf(
            PlaceholderLink("저장", onDone),
        ),
    )
}
