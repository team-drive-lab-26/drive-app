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

val DoranButtonShape = RoundedCornerShape(16.dp)

/** 화면에서 가장 중요한 동작 하나. 주황 바탕, 높이 64dp 이상 */
@Composable
fun DoranPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = DoranButtonShape,
        modifier = modifier.heightIn(min = 64.dp),
        contentPadding = PaddingValues(horizontal = 36.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = DoranPalette.Accent,
            contentColor = DoranPalette.OnAccent,
            disabledContainerColor = DoranPalette.SurfaceHigh,
            disabledContentColor = DoranPalette.InkMute,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** 두 번째 동작. 테두리만 있는 버튼 */
@Composable
fun DoranSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    OutlinedButton(
        onClick = onClick,
        shape = DoranButtonShape,
        modifier = modifier.heightIn(min = 64.dp),
        contentPadding = PaddingValues(horizontal = 32.dp),
        border = BorderStroke(2.dp, if (selected) DoranPalette.Accent else DoranPalette.LineStrong),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent, contentColor = DoranPalette.Ink),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
