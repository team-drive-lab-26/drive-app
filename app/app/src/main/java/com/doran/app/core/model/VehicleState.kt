package com.doran.app.core.model

enum class Gear { P, R, N, D }

data class VehicleState(
    val gear: Gear = Gear.P,
    val isAnyDoorOpen: Boolean = false,
    /** Pleos 차량 서비스에서 값을 받고 있는지. false면 디버그 막대 값만 쓴다 */
    val isConnected: Boolean = false,
) {
    val isDriving: Boolean get() = gear == Gear.D || gear == Gear.R
}
