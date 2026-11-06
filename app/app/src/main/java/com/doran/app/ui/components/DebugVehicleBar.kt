package com.doran.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doran.app.model.Gear

/** 디버그 빌드에서 기어 값을 바꿔 보는 막대 */
@Composable
fun DebugVehicleBar(
    gear: Gear,
    onGearChange: (Gear) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "기어", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Gear.entries.forEach { value ->
            val label = @Composable { Text(text = value.name, fontSize = 24.sp) }
            if (value == gear) {
                Button(onClick = {}, modifier = Modifier.height(64.dp), content = { label() })
            } else {
                OutlinedButton(
                    onClick = { onGearChange(value) },
                    modifier = Modifier.height(64.dp),
                    content = { label() },
                )
            }
        }
    }
}
