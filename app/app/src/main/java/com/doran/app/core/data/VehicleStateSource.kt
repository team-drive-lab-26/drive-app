package com.doran.app.core.data

import com.doran.app.core.model.Gear
import com.doran.app.core.model.VehicleState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface VehicleStateSource {
    val state: StateFlow<VehicleState>
}

class FakeVehicleStateSource : VehicleStateSource {
    private val _state = MutableStateFlow(VehicleState())
    override val state: StateFlow<VehicleState> = _state.asStateFlow()

    fun setGear(gear: Gear) {
        _state.update { it.copy(gear = gear) }
    }
}
