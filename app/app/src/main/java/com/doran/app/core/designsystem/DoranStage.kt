package com.doran.app.core.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 도란이 지금 어떤 모습인지. 화면마다 같은 값을 넘긴다 */
data class DoranFace(
    val mood: DoranMood = DoranMood.IDLE,
    val level: Float = 0f,
    val cheerKey: Long = 0L,
)

const val CALL_HINT = "\"도란아\" 하고 불러 보세요"

/**
 * 도란과 자막 한 줄. 도란을 누르면 호출, 길게 누르면 [onLongPress](디버그 패널).
 * 자막이 비면 호출 방법을 흐리게 보여 준다.
 */
@Composable
fun DoranStage(
    face: DoranFace,
    caption: String,
    onCall: () -> Unit,
    modifier: Modifier = Modifier,
    characterSize: Dp = 320.dp,
    onLongPress: () -> Unit = {},
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        DoranCharacter(
            mood = face.mood,
            level = face.level,
            cheerKey = face.cheerKey,
            modifier = Modifier
                .size(characterSize)
                .semantics { contentDescription = "도란. 누르면 말을 들어요" }
                .pointerInput(onCall, onLongPress) {
                    detectTapGestures(onTap = { onCall() }, onLongPress = { onLongPress() })
                },
        )
        Spacer(Modifier.height(8.dp))
        AnimatedContent(
            targetState = caption,
            transitionSpec = { (fadeIn(tween(320, delayMillis = 80)) + slideInVertically(tween(420, easing = FastOutSlowInEasing)) { it / 8 }) togetherWith fadeOut(tween(200)) },
            label = "caption",
        ) { text ->
            Text(
                text = text.ifBlank { CALL_HINT },
                style = MaterialTheme.typography.displayMedium,
                color = if (text.isBlank()) DoranPalette.InkSoft else DoranPalette.Ink,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.8f),
            )
        }
    }
}
