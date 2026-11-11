package com.doran.app.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// 버튼은 테이프 라벨처럼 모서리를 조금만 굴린다
val DoranButtonShape = RoundedCornerShape(14.dp)

/** 화면에서 가장 중요한 동작 하나. 남색 먹 바탕, 높이 64dp 이상 */
@Composable
fun DoranPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = DoranButtonShape,
        modifier = modifier.heightIn(min = 64.dp),
        contentPadding = PaddingValues(horizontal = 36.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = DoranPalette.Ink,
            contentColor = DoranPalette.Surface,
            disabledContainerColor = DoranPalette.Line,
            disabledContentColor = DoranPalette.InkSoft,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** 두 번째 동작. 테두리만 있는 버튼. 테이프 띠 위에 놓일 때는 [onTape] */
@Composable
fun DoranSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onTape: Boolean = false,
) {
    val ink = if (onTape) DoranPalette.TapeText else DoranPalette.Ink
    OutlinedButton(
        onClick = onClick,
        shape = DoranButtonShape,
        modifier = modifier.heightIn(min = 64.dp),
        contentPadding = PaddingValues(horizontal = 32.dp),
        border = BorderStroke(2.dp, if (selected) DoranPalette.Head else ink.copy(alpha = if (onTape) 0.6f else 0.85f)),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent, contentColor = ink),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
