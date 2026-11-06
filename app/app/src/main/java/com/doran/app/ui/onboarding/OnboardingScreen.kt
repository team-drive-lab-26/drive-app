package com.doran.app.ui.onboarding

import androidx.compose.runtime.Composable
import com.doran.app.ui.components.PlaceholderLink
import com.doran.app.ui.components.PlaceholderScreen

@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
) {
    PlaceholderScreen(
        title = "처음 설정",
        links = listOf(
            PlaceholderLink("설정 끝내기", onDone),
        ),
    )
}
