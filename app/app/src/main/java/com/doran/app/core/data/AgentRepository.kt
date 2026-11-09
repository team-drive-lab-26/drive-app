package com.doran.app.core.data

import com.doran.app.core.network.AgentRequest
import com.doran.app.core.network.AgentResponse
import com.doran.app.core.network.DoranApi
import com.doran.app.core.network.EffectDto
import com.doran.app.core.network.NavigationDto
import com.doran.app.core.network.NetworkModule
import com.doran.app.core.network.TripRecordRequest
import com.doran.app.core.network.VehicleDto

data class AgentReply(
    val status: String,
    val speech: String,
    val caption: String,
    val cards: List<AgentCard>,
    val waypoint: WaypointRequest?,
    val requestedTrackId: String?,
    val requestedCapsuleId: String?,
)

data class AgentCard(val placeId: String?, val trackId: String?, val name: String)

data class WaypointRequest(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val naviPoiId: String,
    val naviPoiSubId: String,
    val address: String,
)

class AgentRepository(baseUrl: String) {
    private val api: DoranApi? = baseUrl.takeIf { it.isNotBlank() }?.let(NetworkModule::doranApi)

    val isConfigured: Boolean get() = api != null

    suspend fun send(
        sessionId: String,
        vehicle: VehicleDto,
        utterance: String? = null,
        event: String? = null,
        navigation: NavigationDto = NavigationDto(),
    ): AgentReply {
        val request = AgentRequest(
            sessionId = sessionId,
            kind = if (event == null) AgentRequest.KIND_UTTERANCE else AgentRequest.KIND_EVENT,
            vehicle = vehicle,
            navigation = navigation,
            utterance = utterance,
            event = event,
        )
        return requireApi().respond(request).toReply()
    }

    suspend fun recordTrip(sessionId: String, type: String, id: String, name: String? = null, category: String? = null) {
        requireApi().recordTrip(TripRecordRequest(sessionId, type, id, name, category))
    }

    private fun requireApi(): DoranApi = checkNotNull(api) { "AI 서버 주소가 없어" }
}

private fun AgentResponse.toReply(): AgentReply {
    val waypoint = effects.firstOrNull { it.type == EffectDto.ADD_WAYPOINT }?.place?.let { place ->
        // 내비 POI ID가 없으면 경유지를 추가할 수 없다
        val poiId = place.naviPoiId ?: return@let null
        WaypointRequest(
            id = place.id,
            name = place.name,
            latitude = place.latitude,
            longitude = place.longitude,
            naviPoiId = poiId,
            naviPoiSubId = place.naviPoiSubId ?: "0",
            address = place.address,
        )
    }
    return AgentReply(
        status = status,
        speech = speech,
        caption = caption,
        cards = cards.map { AgentCard(it.placeId, it.trackId, it.name) },
        waypoint = waypoint,
        requestedTrackId = effects.firstOrNull { it.type == EffectDto.PLAY_MUSIC }?.trackId,
        requestedCapsuleId = effects.firstOrNull { it.type == EffectDto.PLAY_CAPSULE }?.capsuleId,
    )
}
