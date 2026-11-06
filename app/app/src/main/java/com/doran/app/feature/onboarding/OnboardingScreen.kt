package com.doran.app.feature.onboarding

import androidx.compose.runtime.Composable
import com.doran.app.core.designsystem.PlaceholderLink
import com.doran.app.core.designsystem.PlaceholderScreen

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
