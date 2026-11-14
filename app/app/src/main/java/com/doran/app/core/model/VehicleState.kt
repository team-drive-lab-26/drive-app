package com.doran.app.core.model

enum class Gear { P, R, N, D }

data class VehicleState(
    val gear: Gear = Gear.P,
    val isAnyDoorOpen: Boolean = false,
    /** Pleos 차량 서비스에서 값을 받고 있는지. false면 디버그 막대 값만 쓴다 */
    val isConnected: Boolean = false,
    /** EV 배터리 잔량(%). 차량이 알려 주지 않으면 null */
    val batteryPercent: Int? = null,
    /** 안전벨트를 맨 좌석 수. 탑승 인원으로 쓴다 */
    val buckledSeats: Int? = null,
    val isWiperOn: Boolean? = null,
    val isHeadlightOn: Boolean? = null,
) {
    val isDriving: Boolean get() = gear == Gear.D || gear == Gear.R
}
