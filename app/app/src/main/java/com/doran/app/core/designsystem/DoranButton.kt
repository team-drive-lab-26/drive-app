package com.doran.app.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 화면에서 가장 중요한 동작 하나. 먹색 알약, 높이 64dp 이상 */
@Composable
fun DoranPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 64.dp),
        contentPadding = PaddingValues(horizontal = 36.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DoranPalette.Ink, contentColor = DoranPalette.Surface),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** 두 번째 동작. 테두리만 있는 알약 */
@Composable
fun DoranSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 64.dp),
        contentPadding = PaddingValues(horizontal = 32.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) DoranPalette.Body else DoranPalette.Line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = DoranPalette.Ink),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
