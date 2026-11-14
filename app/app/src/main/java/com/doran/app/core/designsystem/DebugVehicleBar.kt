package com.doran.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doran.app.core.model.Gear
import com.doran.app.core.model.VehicleState

/** 디버그 빌드에서 기어와 문 값을 바꿔 보는 막대. 실제 차량 값이 오면 그 값으로 덮인다 */
@Composable
fun DebugVehicleBar(
    vehicle: VehicleState,
    onGearChange: (Gear) -> Unit,
    onDoorChange: (Boolean) -> Unit,
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
            ToggleChip(label = value.name, selected = value == vehicle.gear) { onGearChange(value) }
        }
        Spacer(Modifier.width(12.dp))
        Text(text = "문", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ToggleChip(label = if (vehicle.isAnyDoorOpen) "열림" else "닫힘", selected = vehicle.isAnyDoorOpen) {
            onDoorChange(!vehicle.isAnyDoorOpen)
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = if (vehicle.isConnected) "차량 연결됨" else "차량 미연결",
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ToggleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val content = @Composable { Text(text = label, fontSize = 24.sp) }
    if (selected) {
        Button(onClick = onClick, modifier = Modifier.height(64.dp), content = { content() })
    } else {
        OutlinedButton(onClick = onClick, modifier = Modifier.height(64.dp), content = { content() })
    }
}
