package com.doran.app.feature.record

import androidx.compose.runtime.Composable
import com.doran.app.core.designsystem.PlaceholderLink
import com.doran.app.core.designsystem.PlaceholderScreen

@Composable
fun RecordScreen(
    onRecorded: () -> Unit,
) {
    PlaceholderScreen(
        title = "한마디 남길래?",
        links = listOf(
            PlaceholderLink("녹음 끝", onRecorded),
        ),
    )
}
