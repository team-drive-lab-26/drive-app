package com.doran.app.core.model

enum class Gear { P, R, N, D }

data class VehicleState(
    val gear: Gear = Gear.P,
    val isAnyDoorOpen: Boolean = false,
) {
    val isDriving: Boolean get() = gear == Gear.D || gear == Gear.R
}
