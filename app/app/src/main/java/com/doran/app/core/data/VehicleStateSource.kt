package com.doran.app.core.data

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.content.Context
import android.util.Log
import com.doran.app.core.model.Gear
import com.doran.app.core.model.VehicleState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface VehicleStateSource {
    val state: StateFlow<VehicleState>
    fun start() = Unit
    fun release() = Unit

    /** 디버그 막대에서 기어를 바꾼다. 실제 차량 값이 오면 그 값으로 덮인다 */
    fun overrideGear(gear: Gear)

    /** 디버그 막대에서 문 열림을 바꾼다 */
    fun overrideDoorOpen(open: Boolean)
}

class FakeVehicleStateSource : VehicleStateSource {
    private val _state = MutableStateFlow(VehicleState())
    override val state: StateFlow<VehicleState> = _state.asStateFlow()

    override fun overrideGear(gear: Gear) = _state.update { it.copy(gear = gear) }
    override fun overrideDoorOpen(open: Boolean) = _state.update { it.copy(isAnyDoorOpen = open) }
}

/** Pleos Vehicle SDK에서 기어, 문 열림, 배터리, 안전벨트, 와이퍼, 전조등을 받는다. 연결에 실패하면 디버그 값만 남는다 */
class PleosVehicleStateSource(context: Context) : VehicleStateSource {
    private val vehicle = Vehicle(context.applicationContext)
    private val _state = MutableStateFlow(VehicleState())
    override val state: StateFlow<VehicleState> = _state.asStateFlow()

    // 문마다 열린 정도(0이면 닫힘)를 따로 들고 하나라도 열려 있으면 열림으로 본다
    private val doorPositions = mutableMapOf<DoorValue, Int>()
    private val doorListeners = mutableMapOf<DoorValue, DoorPositionListener>()
    private val buckled = mutableMapOf<SeatValue, Boolean>()
    private val beltListeners = mutableMapOf<SeatValue, SeatBeltBuckledStateListener>()
    private var started = false

    private val batteryListener = object : EvBatteryStateOfChargeListener {
        override fun onEvBatteryStateOfChargeUpdated(percent: Int?) {
            if (percent != null) _state.update { it.copy(batteryPercent = percent, isConnected = true) }
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "배터리 잔량을 받지 못함", e)
        }
    }

    private val wiperListener = object : WiperStateListener {
        override fun onWiperStateUpdated(state: WiperState?) {
            if (state != null) _state.update { it.copy(isWiperOn = state == WiperState.ON) }
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "와이퍼 상태를 받지 못함", e)
        }
    }

    private val headlightListener = object : LightStateListener {
        // 주간주행등은 낮에도 켜져 있어 전조등을 켠 것으로 보지 않는다
        override fun onLightStateUpdated(state: LightState?) {
            if (state != null) _state.update { it.copy(isHeadlightOn = state == LightState.ON) }
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "전조등 상태를 받지 못함", e)
        }
    }

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            val mapped = gear?.toGear() ?: return
            _state.update { it.copy(gear = mapped, isConnected = true) }
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "기어 값을 받지 못함", e)
        }
    }

    override fun start() {
        if (started) return
        try {
            vehicle.initialize()
            val gearApi = vehicle.getGear()
            gearApi.registerGearSelection(gearListener)
            gearApi.getCurrentGearSelectionState({ gearListener.onSelected(it) }, {})
            PASSENGER_DOORS.forEach(::watchDoor)
            watchExtras()
            started = true
        } catch (e: Exception) {
            // 차량 서비스가 없는 기기에서는 디버그 막대 값으로 시연한다
            Log.w(TAG, "차량 서비스 연결 실패", e)
        }
    }

    private fun watchDoor(door: DoorValue) {
        val area = DoorArea(door)
        val listener = object : DoorPositionListener {
            override fun onPositionUpdated(position: Int?) = updateDoor(door, position ?: 0)
            override fun onFailed(e: Exception) {
                Log.w(TAG, "문 상태를 받지 못함: $door", e)
            }
        }
        try {
            vehicle.getDoor().registerDoorPosition(area, listener)
            vehicle.getDoor().getCurrentDoorPosition(area, { updateDoor(door, it ?: 0) }, { e -> listener.onFailed(e) })
            doorListeners[door] = listener
        } catch (e: Exception) {
            // 이 차량에 없는 문은 건너뛴다
            Log.w(TAG, "문 리스너 등록 실패: $door", e)
        }
    }

    // 차종마다 없는 값이 있어 하나씩 따로 등록하고, 실패한 것은 null로 둔다
    private fun watchExtras() {
        tryRegister("배터리") {
            vehicle.getEvBattery().registerEvBatteryStateOfCharge(batteryListener)
            vehicle.getEvBattery().getEvBatteryStateOfCharge({ batteryListener.onEvBatteryStateOfChargeUpdated(it) }, {})
        }
        tryRegister("와이퍼") {
            val front = WindowArea(WindowValue.FRONT_WINDSHIELD)
            vehicle.getWiper().registerWiperState(front, wiperListener)
            vehicle.getWiper().getCurrentWiperState(front, { wiperListener.onWiperStateUpdated(it) }, {})
        }
        tryRegister("전조등") {
            vehicle.getLight().registerLowBeamLightState(headlightListener)
            vehicle.getLight().getCurrentLowBeamLightState({ headlightListener.onLightStateUpdated(it) }, {})
        }
        SEATS.forEach(::watchBelt)
    }

    private fun watchBelt(seat: SeatValue) {
        val area = SeatArea(seat)
        val listener = object : SeatBeltBuckledStateListener {
            override fun onSeatBeltBuckledStateUpdated(isBuckled: Boolean?) = updateBelt(seat, isBuckled == true)
            override fun onFailed(e: Exception) {
                Log.w(TAG, "안전벨트 상태를 받지 못함: $seat", e)
            }
        }
        tryRegister("안전벨트 $seat") {
            vehicle.getSeat().registerSeatBeltBuckledState(area, listener)
            vehicle.getSeat().getCurrentSeatBeltBuckledState(area, { updateBelt(seat, it == true) }, {})
            beltListeners[seat] = listener
        }
    }

    private fun updateBelt(seat: SeatValue, isBuckled: Boolean) {
        val count = synchronized(buckled) {
            buckled[seat] = isBuckled
            buckled.values.count { it }
        }
        _state.update { it.copy(buckledSeats = count) }
    }

    private inline fun tryRegister(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "$name 리스너 등록 실패", e)
        }
    }

    private fun updateDoor(door: DoorValue, position: Int) {
        synchronized(doorPositions) { doorPositions[door] = position }
        val open = synchronized(doorPositions) { doorPositions.values.any { it > 0 } }
        _state.update { it.copy(isAnyDoorOpen = open, isConnected = true) }
    }

    override fun overrideGear(gear: Gear) = _state.update { it.copy(gear = gear) }
    override fun overrideDoorOpen(open: Boolean) = _state.update { it.copy(isAnyDoorOpen = open) }

    override fun release() {
        if (!started) return
        try {
            vehicle.getGear().unregisterGearSelection(gearListener)
            doorListeners.forEach { (door, listener) -> vehicle.getDoor().unregisterDoorPosition(DoorArea(door), listener) }
            beltListeners.forEach { (seat, listener) -> runCatching { vehicle.getSeat().unregisterSeatBeltBuckledState(SeatArea(seat), listener) } }
            runCatching { vehicle.getEvBattery().unregisterEvBatteryStateOfCharge(batteryListener) }
            runCatching { vehicle.getWiper().unregisterWiperState(WindowArea(WindowValue.FRONT_WINDSHIELD), wiperListener) }
            runCatching { vehicle.getLight().unregisterLowBeamLightState(headlightListener) }
            vehicle.release()
        } catch (_: Exception) {
        }
        doorListeners.clear()
        beltListeners.clear()
        started = false
    }

    private companion object {
        const val TAG = "DoranVehicle"
        val PASSENGER_DOORS = listOf(DoorValue.ROW_1_LEFT, DoorValue.ROW_1_RIGHT, DoorValue.ROW_2_LEFT, DoorValue.ROW_2_RIGHT)
        val SEATS = listOf(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT, SeatValue.ROW_2_LEFT, SeatValue.ROW_2_CENTER, SeatValue.ROW_2_RIGHT)
    }
}

private fun VehicleGear.toGear(): Gear? = when (this) {
    VehicleGear.GEAR_PARK -> Gear.P
    VehicleGear.GEAR_REVERSE -> Gear.R
    VehicleGear.GEAR_NEUTRAL -> Gear.N
    VehicleGear.GEAR_UNKNOWN -> null
    // 수동 단수(1~9)와 D는 모두 전진 주행이다
    else -> Gear.D
}
